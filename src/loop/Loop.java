package loop;

import loop.ast.script.Unit;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.StringReader;
import java.lang.reflect.Proxy;

import loop.runtime.LoopInvocationHandler;

/**
 * Converts parsed, type-solved, emitted code to Java classes.
 */
public class Loop {

  public static void main(String[] args) {
    if (args.length == 0) {
      LoopShell.shell();
    }

    try {
      if (args.length > 1)
        run(args[0], args);
      else
        run(args[0]);
    } catch (FileNotFoundException e) {
      System.out.println("No such file: " + e.getMessage());
      System.out.println();
      System.exit(1);
    }
  }

  public static Object run(String file) throws FileNotFoundException {
    Executable unit = loopCompile(file);
    unit.runMain(true);

    return safeEval(unit, null);
  }

  public static Object run(String file, String[] args) throws FileNotFoundException {
    Executable unit = loopCompile(file);
    unit.runMain(true);

    return safeEval(unit, args);
  }

  public static Object evalClassOrFunction(String function,
                                           Unit shellScope) {
    Executable executable = new Executable(new StringReader(function));
    try {
      executable.compileClassOrFunction(shellScope);
    } catch (Exception e) {
      e.printStackTrace();
      return new LoopError("malformed function");
    }

    if (executable.hasErrors()) {
      executable.printStaticErrorsIfNecessary();
      return "";
    }

    return "ok";
  }

  static Object safeEval(Executable executable, String[] args) {
    if (executable.runMain()) {
      return executable.main(args);
    } else {
      executable.getCompiled();   // Forces class to be loaded & initialized.
      return null;
    }
  }

  /**
   * Compiles the specified file into a binary Java executable.
   */
  public static Class<?> compile(String file) {
    try {
      return loopCompile(file).getCompiled();
    } catch (FileNotFoundException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Returns an executable that represents the compiled form of the Loop program.
   * <p/>
   * See {@link Executable} for more details on the compilation process.
   */
  private static Executable loopCompile(String file) throws FileNotFoundException {
    Executable executable;
    File script = new File(file);
    executable = new Executable(new FileReader(script), script.getName());
    executable.compile();
    if (executable.hasErrors()) {
      String errors = executable.printStaticErrorsIfNecessary();

      throw new LoopCompileException("Syntax errors exist:\n" + errors, executable);
    }
    return executable;
  }

  public static void error(String error) {
    throw new LoopExecutionException(error);
  }
  
  /**
   * Returns an implementation of the given Java interface that
   * is backed by the specified Loop module. The generated Java class is loaded into
   * the common runtime Loop class loader. See {@link LoopClassLoader} for details.
   *
   * @param iface A Java interface that you wish to implement using Loop
   * @param module The name of a Loop module minus the '.loop' extension. This name may
   *               contain a path-prefix from the current directory.
   */
  @SuppressWarnings("unchecked")
  public static <I> I implement(Class<I> iface, String module) {
    if (!iface.isInterface()) {
      throw new RuntimeException(iface + " is not an interface ");
    }

    return (I) Proxy.newProxyInstance(LoopClassLoader.CLASS_LOADER, new Class[]{ iface },
        new LoopInvocationHandler(iface, module));
  }
}
