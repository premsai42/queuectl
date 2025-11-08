package com.example.queuectl.shell;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * Run commands from a script file. Each non-empty, non-comment line must be a valid CLI command.
 * Example commands:
 *   enqueue '{"id":"job1","command":"echo hi"}'
 *   worker start 2
 *   config set max_retries 2
 */
@ShellComponent
public class ScriptCommands {

    @Autowired
    private ApplicationContext context;

    @ShellMethod(key = "script", value = "Execute commands from a script file line by line.")
    public String runScript(String filePath) throws IOException {
        Path path = Paths.get(filePath);
        if (!Files.exists(path)) {
            return "❌ File not found: " + filePath;
        }

        List<String> lines = Files.readAllLines(path);
        int executed = 0;

        for (String raw : lines) {
            String line = raw == null ? "" : raw.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;

            System.out.println("▶ " + line);

            try {
                // dispatch by prefix
                if (line.startsWith("enqueue ")) {
                    // everything after 'enqueue ' is the JSON arg (keep quotes as-is)
                    String arg = line.substring("enqueue ".length()).trim();
                    invokeBeanMethod("com.example.queuectl.shell.EnqueueCommands", "enqueue", new Class[]{String.class}, new Object[]{arg});
                } else if (line.startsWith("worker start")) {
                    String[] parts = line.split("\\s+");
                    int count = 1;
                    if (parts.length >= 3) {
                        try { count = Integer.parseInt(parts[2]); } catch (NumberFormatException ignored) {}
                    }
                    invokeBeanMethod("com.example.queuectl.shell.WorkerCommands", "start", new Class[]{int.class}, new Object[]{count});
                } else if (line.startsWith("worker stop")) {
                    invokeBeanMethod("com.example.queuectl.shell.WorkerCommands", "stop", new Class[0], new Object[0]);
                } else if (line.startsWith("config set")) {
                    // config set key value
                    String[] parts = line.split("\\s+", 4);
                    if (parts.length >= 4) {
                        String key = parts[2];
                        String value = parts[3];
                        invokeBeanMethod("com.example.queuectl.shell.ConfigCommands", "set", new Class[]{String.class, String.class}, new Object[]{key, value});
                    } else {
                        System.out.println("⚠ Invalid config set syntax. Expect: config set <key> <value>");
                    }
                } else if (line.equals("status")) {
                    Object res = invokeBeanMethod("com.example.queuectl.shell.StatusCommands", "status", new Class[0], new Object[0]);
                    if (res != null) System.out.println(res.toString());
                } else if (line.startsWith("list")) {
                    String[] parts = line.split("\\s+", 2);
                    String state = parts.length > 1 ? parts[1].trim() : "";
                    Object res = invokeBeanMethod("com.example.queuectl.shell.ListCommands", "list", new Class[]{String.class}, new Object[]{state});
                    if (res != null) System.out.println(res.toString());
                } else if (line.startsWith("dlq list")) {
                    Object res = invokeBeanMethod("com.example.queuectl.shell.DlqCommands", "list", new Class[0], new Object[0]);
                    if (res != null) System.out.println(res.toString());
                } else if (line.startsWith("dlq retry")) {
                    String[] parts = line.split("\\s+", 3);
                    if (parts.length >= 3) {
                        String id = parts[2];
                        invokeBeanMethod("com.example.queuectl.shell.DlqCommands", "retry", new Class[]{String.class}, new Object[]{id});
                    } else {
                        System.out.println("⚠ Invalid dlq retry syntax. Expect: dlq retry <id>");
                    }
                } else {
                    System.out.println("⚠ Unknown or unsupported scripted command: " + line);
                }
            } catch (Exception e) {
                System.out.println("⚠ Error executing command: " + e.getMessage());
            }

            executed++;
        }

        return "✅ Executed " + executed + " commands from " + filePath;
    }

    /**
     * Helper that resolves bean by full class name and invokes the requested method reflectively.
     * Uses bean type lookup to avoid tight compile-time coupling.
     */
    private Object invokeBeanMethod(String fullClassName, String methodName, Class<?>[] paramTypes, Object[] params) throws Exception {
        Class<?> clz;
        try {
            clz = Class.forName(fullClassName);
        } catch (ClassNotFoundException cnf) {
            // try simple lookup by short name (in case package differs)
            String shortName = fullClassName.substring(fullClassName.lastIndexOf('.') + 1);
            Object bean = findBeanBySimpleName(shortName);
            if (bean == null) {
                throw new ClassNotFoundException("Bean class not found: " + fullClassName + " and no bean named " + shortName);
            }
            clz = bean.getClass();
            return invokeMethodOnBean(bean, methodName, paramTypes, params);
        }

        Object bean;
        try {
            bean = context.getBean(clz);
        } catch (Exception e) {
            // fallback: try find bean by simple name
            String simple = clz.getSimpleName();
            bean = findBeanBySimpleName(simple);
            if (bean == null) {
                throw new IllegalStateException("No Spring bean found for class: " + fullClassName);
            }
        }

        return invokeMethodOnBean(bean, methodName, paramTypes, params);
    }

    private Object invokeMethodOnBean(Object bean, String methodName, Class<?>[] paramTypes, Object[] params) throws Exception {
        Method m = null;
        try {
            m = bean.getClass().getMethod(methodName, paramTypes);
        } catch (NoSuchMethodException e) {
            // try to find any method with same name and compatible parameter count
            for (Method mm : bean.getClass().getMethods()) {
                if (mm.getName().equals(methodName) && mm.getParameterCount() == paramTypes.length) {
                    m = mm;
                    break;
                }
            }
            if (m == null) {
                throw new NoSuchMethodException("Method " + methodName + " not found on " + bean.getClass().getName());
            }
        }

        try {
            return m.invoke(bean, params);
        } catch (InvocationTargetException ite) {
            throw new Exception("Invocation error: " + ite.getTargetException().getMessage(), ite.getTargetException());
        }
    }

    private Object findBeanBySimpleName(String simpleName) {
        String lower = simpleName.substring(0,1).toLowerCase() + simpleName.substring(1);
        // try bean name by convention
        if (context.containsBean(lower)) {
            return context.getBean(lower);
        }
        // fallback: search all beans for matching simple class name
        String[] names = context.getBeanDefinitionNames();
        for (String name : names) {
            Object b = context.getBean(name);
            if (b.getClass().getSimpleName().equals(simpleName)) return b;
        }
        return null;
    }
}
