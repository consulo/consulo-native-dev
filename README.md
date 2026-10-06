# Native Development

Shared native development support for Consulo language plugins (C/C++, Rust, Go and others):

- native toolchains (`perf`, `dtrace`, `addr2line`/`llvm-symbolizer`);
- native profilers built on the platform profiler API;
- native symbols, navigation and demangling.

Plugin id: `consulo.nativeDev`.

## Modules

| Module | Artifact | JPMS module | Content |
|---|---|---|---|
| `api` | `consulo.nativeDev.api` | `consulo.nativeDev.api` | API and extension points for language plugins; binary readers (`consulo.nativeDev.binary`: ELF) |
| `impl` | `consulo.nativeDev.impl` | `consulo.nativeDev.impl` | Native debug runner and the "Native Application" run configuration |
| `profiler/api` | `consulo.nativeDev.profiler.api` | `consulo.nativeDev.profiler.api` | Native profiler API: `NativeProfilableRunProfile`, `NativeProfilerLauncher` (patch, start and profile a command line), the Native profiler settings page |
| `profiler/perf` | `consulo.nativeDev.perf` | `consulo.nativeDev.perf` | `perf` profiler (Linux): "Profile with ▸ perf" for native run profiles, `perf script` and `perf.data` snapshots |
| `profiler/dtrace` | `consulo.nativeDev.dtrace` | `consulo.nativeDev.dtrace` | DTrace profiler (macOS), base for language integrations |
| `debugger/api` | `consulo.nativeDev.debugger.api` | `consulo.nativeDev.debugger.api` | Native debugger API: `NativeDebugProcess`, debugger providers, line breakpoint type, run profile hand-off, driver contract (`consulo.nativeDev.debugger.driver`) and the shared driver-based XDebugger process |
| `debugger/mi` | `consulo.nativeDev.debugger.mi` | `consulo.nativeDev.debugger.mi` | GDB/MI protocol from Apache NetBeans: parser, records, command manager, gdb version peculiarities |
| `debugger/gdb` | `consulo.nativeDev.debugger.gdb` | `consulo.nativeDev.debugger.gdb` | gdb backend: `GdbMiDriver` and the gdb debugger provider |
| `debugger/dap` | `consulo.nativeDev.debugger.dap` | `consulo.nativeDev.debugger.dap` | DAP backends on consulo-dap: lldb-dap, CodeLLDB, cppdbg (Windows), gdb DAP |
| `plugin` | `consulo.nativeDev` | `consulo.nativeDev` | The plugin itself |

`native` is a Java keyword, so Java modules and packages are named `consulo.nativeDev`.

## Build

```
mvn package
```

Run the IDE with the plugin from `plugin/`:

```
mvn consulo:run-desktop-awt-fork
```

## License

Apache License, Version 2.0 (see [LICENSE.txt](LICENSE.txt)).

Code ported from other projects keeps its own license and file headers (see [NOTICE.txt](NOTICE.txt)). The ELF reader
in `consulo.nativeDev.binary` comes from Eclipse CDT and is licensed under the Eclipse Public License 2.0 (see
[LICENSE-EPL-2.0.txt](LICENSE-EPL-2.0.txt)).
