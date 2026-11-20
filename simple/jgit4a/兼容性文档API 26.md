# jgit4a Android 兼容性改造清单（修订三版）

从 6.10 到最新的 7.8，JGit 对普通 Git 用户基本没有"新功能级"的惊喜，核心变化是运行基线升到 Java 17 并清理了大量旧 API，然后补了 SSH 签名、SSH 主机证书、Union merge、多语言 diff hunk header、pack-refs/describe/add 等少量命令能力，以及针对大仓库的 multi-pack index 和 object size index 这类"仓库越大越有感"的性能优化；真正容易被感知的反而是几处行为变严——比如拒绝 refs/main 这种单层 ref、ignore 反斜杠转义修正、signed push + push option 行为修正——所以它更像一次"底层加固 + 大仓库提速 + 安全边界收紧"的升级，而不是功能爆发。

所以6.10可以长期使用。

**基线**：minSdk 26（API 26 / Android 8.0）｜ JGit **6.10**（固定，不回退 5.x）｜ 已启用核心库脱糖（AGP 7.4+，`desugar_jdk_libs` 2.0+，建议 2.1.x）

**本文口径**：只写“必须手动做”的部分；判定依据只有两条——API 26 的 ART 上是否可用、核心库脱糖是否能补上。两条都不成立的项，一律不进正文。

---

## 0. 本轮审查：改了哪 10 处

上一版整体方向正确（先划脱糖边界、只留必做项、把 AI 清单降为参考），但存在 **3 处会误导施工的实质错误**、**1 处自相矛盾**、**8 类漏项**。逐条如下。

| 编号 | 问题 | 性质 | 处置 |
|---|---|---|---|
| Q1 | 把 `String.strip / stripLeading / stripTrailing / isBlank / lines / repeat`、`Collection.toArray(IntFunction)`、`Predicate.not` 判为“脱糖补不了、必做” | **实质错误**：这几项自 `desugar_jdk_libs` 2.0（AGP 7.4+）起就在官方 Java 11+ API 脱糖清单内，与本文“已启用 2.x 脱糖”的前提自相矛盾 | 从“必改”移到 §4“先验证再删” |
| Q2 | 用“接口静态方法不能脱糖”作为 `List.of` 无法脱糖的理由 | **实质错误**：`Predicate.not` 正是接口静态方法且被脱糖支持，理由不成立 | 换成真实原因：脱糖库没有提供这些 API 的实现 |
| Q3 | `lines()` → `split("\\R", -1)` 的语义差异只写了“空输入 / 末尾空行” | **实质错误**：`\R` 还匹配 `\u0085`、`\u2028`、`\u2029`，而 `lines()` 只在 `\n`、`\r`、`\r\n` 处切分 | 补“会多切行”这条差异 |
| Q4 | §3 写“ATOMIC_MOVE 涉及 11 个文件”，随后列 FileUtils 5 + GC 4 + RefDirectoryRename 4 + FileReftableStack 2 + ObjectDirectoryPackParser 2，相加已 17 | **自相矛盾**，且分组数字无出处 | 改为“以扫描为准”，删除分组数字 |
| Q5 | 漏项：`Collectors.toUnmodifiable*`（Java 10）、`Stream.ofNullable / takeWhile / dropWhile`（Java 9）、`Stream.iterate` 三参（Java 9）、`Optional.ifPresentOrElse / stream`（Java 9）、`Optional.orElseThrow()` 无参（Java 10）、`Files.readString / writeString`、`Path.of`（Java 11）、`ByteArrayOutputStream.toString(Charset)`（Java 10）、`Objects.requireNonNullElse*`（Java 9）、`Thread.onSpinWait`（Java 9） | 漏项 | 补进 §3，并加 §9 待扫描清单 |
| Q6 | 断言 `Optional.or` “脱糖库没有” | 无出处的断言 | 改为“不在官方 Java 11+ 支持表内，按必做处理，可实测降级为免做” |
| Q7 | 未提 animal-sniffer 与脱糖的交互：脱糖不改源码与 javac 产物，闸门仍会把脱糖已覆盖的调用报成错误 | 实用缺口 | §7 补“白名单与告警治理” |
| Q8 | 断言“脱糖会把 `java.time` 重写为 `j$.time`”，并称后果是“类型不匹配或 NoSuchMethodError” | 表述不准：AGP 在 minSdk ≥ 26 时通常不重写 `java.time`；真被重写时典型表现是 dex 阶段类型不匹配 / 运行时链接错误 | 改为“检查 dex 里有没有 `j$.time` 包”的可执行验证项 |
| Q9 | 数量仍以“约 90–100 处 / 50–60 个文件”为主 | 估算，无法核对 | 保留但标注为估算，不作验收依据 |
| Q10 | 把“确认 D8/R8 正常处理 class version 55”当作风险项 | 不是风险：D8 处理 Java 11 字节码是长期能力 | 改为配置项，并补 minSdk 26 对 `invoke-custom` 的实际收益 |

**一句话结论**：方向、顺序都不用改；但上一版把“已由脱糖覆盖的 7 个 API”算进了手工工作量，真实手工量比它写的更小——而 `List.of` 系列、`Stream.toList`、`java.io` 那批、`Objects.*` 那批、`Math` 的新重载、nio 的 Java 11 新方法，一个都省不掉。

---

## 1. 判定基线：minSdk 26 给了什么、没给什么

### 1.1 API 26 原生可用（不用做，也不用脱糖）

| 能力 | 说明 |
|---|---|
| `java.nio.file.*` 基础类（`Path` / `Paths` / `Files` / `FileSystem` / `FileStore` / `DirectoryStream` / `FileTime` / `BasicFileAttributes` / `StandardOpenOption` / `StandardCopyOption` / `LinkOption` / `PathMatcher`）与常用 `Files.*` | Android 8.0 起正式可用；API 24/25 上是隐藏残缺实现，不能作为目标 |
| `java.util.stream.*`、`java.util.function.*`、`Optional`（Java 8 形态）、`Map / Collection / Comparator` 的 Java 8 新增方法 | API 24 起 |
| `java.time.*`（`Instant` / `Duration` / `ZoneId` / `LocalDate` 等） | API 26 起原生 |
| `java.lang.invoke`（`MethodHandle`、`invoke-custom`） | API 26 起原生 —— **这正是 minSdk 26 的一项实际收益：lambda / 方法引用不需要 D8 再做 `invoke-custom` 降级** |
| `FileChannel` / `FileLock` / `tryLock` | 可用（多进程锁的实际语义需真机验证） |

### 1.2 API 26 有 `java.nio.file`，但没有其中 Java 11 新增的方法（最易被漏掉）

`Path.of(...)`、`Files.readString(...)`、`Files.writeString(...)`、`Files.mismatch(...)`（Java 12）等都不在 API 26。**包的可用 ≠ 包里所有方法可用**——这是“java.nio.file 基础类不用做”这句话容易带出的误区。涉及处按 §3 处理。

### 1.3 脱糖能补什么（决定工作量）

核心库脱糖是**编译期字节码改写**：D8/R8 把调用点重写到随 APK 打包的补齐库（`j$.xxx` 前缀）。它只改字节码，不改源码，也不改变“javac 能不能编译过”——所以**源码里写 `List.of`，在 compileSdk 34 下照样编译通过，只是运行时崩**。这决定了：能不能拦住，只能靠 animal-sniffer / Lint，不能指望编译报错。

---

## 2. 脱糖覆盖边界（修正版）

分三档，不要再用“能 / 不能”二分。

### 2.1 已覆盖——确定不用做

| 类别 | 覆盖来源 |
|---|---|
| `java.util.stream.*` 基础形态（`Stream` / `IntStream` / `Collectors` 的 Java 8 方法） | API 24 原生；更低 minSdk 走脱糖 |
| `java.util.function.*` 基础形态 + `Predicate.not` | 前者 API 24 原生；`Predicate.not` 由 `desugar_jdk_libs` 2.0+ 提供 |
| `java.util.Optional` 基础形态 + `Optional.isEmpty()` | 前者 API 24 原生；`isEmpty()` 由 2.0+ 提供 |
| `java.util.Map / Collection / Comparator` 的 Java 8 新增方法 | API 24 原生 |
| `java.util.concurrent.atomic` 部分新方法、`ConcurrentHashMap` 部分新方法 | 脱糖 |
| `java.time.*`（`desugar_jdk_libs` full 变体） | API 26 原生；低版本走脱糖 |
| `java.nio.file` 基础类与常用 `Files.*` | API 26 原生（<26 场景另有 nio 脱糖变体） |

### 2.2 已覆盖——但要按 §4 验证后再删（上一版误判为必做）

| API | 引入版本 | 覆盖来源 |
|---|---|---|
| `String.strip / stripLeading / stripTrailing / isBlank / lines / repeat(int)` | Java 11 | `desugar_jdk_libs` 2.0+（AGP 7.4+）官方 Java 11+ API 脱糖清单 |
| `Collection.toArray(IntFunction)`（即 `list.toArray(String[]::new)`） | Java 11 | 同上 |
| `Optional.isEmpty()` | Java 11 | 同上 |

> 前提必须成立：`desugar_jdk_libs` ≥ 2.0 且 AGP ≥ 7.4。用 1.x 或 AGP 7.3 以下，这几项回到必做。

### 2.3 不覆盖——必须手动改

| API | 引入版本 |
|---|---|
| `List.of / Set.of / Map.of / Map.ofEntries` | Java 9 |
| `List.copyOf / Set.copyOf / Map.copyOf` | **Java 10**（不是 9） |
| `Stream.toList()` | Java 16 |
| `Collectors.toUnmodifiableList / Set / Map` | Java 10 |
| `Stream.ofNullable / takeWhile / dropWhile / iterate(seed, hasNext, next)` | Java 9 |
| `Optional.or / ifPresentOrElse / stream` | Java 9（`or` 可实测后降级） |
| `Optional.orElseThrow()` 无参版 | Java 10 |
| `InputStream.readAllBytes / readNBytes / transferTo` | Java 9 |
| `OutputStream.nullOutputStream` | Java 11 |
| `Objects.requireNonNull(T, Supplier) / requireNonNullElse* / checkFromIndexSize / checkFromToIndex / checkIndex` | Java 9 |
| `Math.floorDiv(long,int) / floorMod(long,int) / multiplyExact(long,int) / (int,long)` | Java 9 |
| `Files.readString / writeString / Path.of` | Java 11 |
| `ByteArrayOutputStream.toString(Charset)` | Java 10 |
| `Thread.onSpinWait` | Java 9 |
| `VarHandle`、`Cleaner`、`ProcessHandle`、`StackWalker` | Java 9 |
| `ByteBuffer.slice(int,int)` | Java 13 |
| 反射 / `MethodHandle` / 字符串拼出来的调用 | —（任何静态工具都不可靠，靠 §8 真机验证） |

> 纠偏：`List.of` 是 Java 9，不是 10/11；`copyOf`、`Collectors.toUnmodifiable*`、`ByteArrayOutputStream.toString(Charset)`、`orElseThrow()` 是 Java 10；`Stream.toList` 是 Java 16。

---

## 3. 必改清单（脱糖补不了，唯一必须动源码的部分）

| # | API | 替换方案 | 替换引入的语义差异（必须回归） |
|---|---|---|---|
| 1 | `List.of / Set.of / Map.of / Map.ofEntries` | `Collections.unmodifiableList(Arrays.asList(...))` / `unmodifiableSet` / `unmodifiableMap`；零元素时用 `Collections.emptyList() / emptySet() / emptyMap()` | `of` 拒绝 null（NPE）、`Set.of` / `Map.of` 拒绝重复（IAE）；`unmodifiableXxx` **全部静默接受**，原本靠异常兜底的分支会变静默 → 补单测 |
| 2 | `List.copyOf / Set.copyOf / Map.copyOf` | `Collections.unmodifiableList(new ArrayList<>(src))` 等 | `copyOf` 拒绝 null 元素、返回独立副本；包装视图会随原集合变化，有逃逸风险 |
| 3 | `Stream.toList()` | `Collections.unmodifiableList(collect(Collectors.toList()))` | `toList()` 不可变、`Collectors.toList()` 可变；依赖不可变性的地方必须外包一层 |
| 4 | `Collectors.toUnmodifiableList / toUnmodifiableSet / toUnmodifiableMap` | `collect(Collectors.collectingAndThen(Collectors.toList(), Collections::unmodifiableList))` 等 | 原方法拒绝 null 元素；手工版本在 `toList()` 阶段静默接受 null |
| 5 | `String.strip / stripLeading / stripTrailing`（仅当 §4 验证判为未覆盖） | `trim()` | `strip` 按 `Character.isWhitespace` 判定（含 `\u3000` 全角空格、`\u2000` 系），`trim()` 只删 ≤ `U+0020`。配置解析 / 用户输入处必须确认无全角空白 |
| 6 | `String.lines()`（同上） | `split("\\R", -1)` + 前置判空 | `lines()` 只在 `\n`、`\r`、`\r\n` 切分；`\R` **还会**在 `\u0085`(NEL)、`\u2028`(LS)、`\u2029`(PS) 处切分 → 会多切行。文本可能含这些字符时（国际化资源、富文本）改手写扫描 |
| 7 | `String.repeat(int)` / `isBlank()`（同上） | 手写循环 / `trim().isEmpty()` | `isBlank()` 用 Unicode 空白，`trim().isEmpty()` 漏全角空格 |
| 8 | `readAllBytes` / `readNBytes` / `transferTo` / `nullOutputStream` | 手写 `ByteArrayOutputStream` 读循环、手写 `transferTo`、`OutputStream` 空实现 | `readAllBytes` 一次性入堆，大 pack / 大 blob 有 OOM 风险 → 大文件改流式读 |
| 9 | `Objects.requireNonNull(T, Supplier)` | `if (obj == null) throw new NullPointerException(supplier.get());` | **不能**写 `requireNonNull(obj, supplier.get())`：类型不符且丢惰性 |
| 10 | `Objects.checkFromIndexSize / checkFromToIndex / checkIndex` | 手写 `if` 抛 `IndexOutOfBoundsException` | 异常消息与 JDK 不完全一致，别依赖消息文本 |
| 11 | `Objects.requireNonNullElse / requireNonNullElseGet` | 三元 / 手写 if | 语义直白，无坑 |
| 12 | `Optional.or(Supplier)` | `opt.isPresent() ? opt : supplier.get()` | `or` 惰性；三元必须保证 supplier 只在需要时求值 |
| 13 | `Optional.ifPresentOrElse / stream` | 手写 if-else / `opt.map(Stream::of).orElseGet(Stream::empty)` | `stream()` 的空集语义要一致 |
| 14 | `Stream.ofNullable / takeWhile / dropWhile / iterate` 三参 | `x == null ? Stream.empty() : Stream.of(x)` / 手写循环 + 标志位 | `takeWhile` / `dropWhile` 在**有序流**上的短路语义，手写时容易写成全量过滤 |
| 15 | `Math.floorDiv(long,int) / floorMod(long,int) / multiplyExact(long,int) / (int,long)` | API 26 只有 `(int,int)` 与 `(long,long)`：把 int 实参显式转 long 走 long 重载 | 转换后返回类型变 long，调用点可能需要收窄；`multiplyExact` 溢出仍抛 `ArithmeticException` |
| 16 | `Files.readString / writeString` | `new String(Files.readAllBytes(p), UTF_8)` / `Files.write(p, s.getBytes(UTF_8))` | `readString` 对非法字节抛 `MalformedInputException`（严格解码），`new String(bytes)` 是替换字符静默降级 → 需要严格时显式 `CharsetDecoder` |
| 17 | `Path.of(...)` | `Paths.get(...)` | 无 |
| 18 | `ByteArrayOutputStream.toString(Charset)` | `toString(charset.name())`（需捕 `UnsupportedEncodingException`）或 `new String(baos.toByteArray(), cs)` | 前者走异常路径，后者多一次拷贝 |
| 19 | `Thread.onSpinWait` | 空实现 / `Thread.yield()` | 无功能差异，只影响自旋性能 |
| 20 | `VarHandle` / `Cleaner` / `ProcessHandle` / `StackWalker` / `ByteBuffer.slice(int,int)` | 按用途替换（`AtomicXxx` / `PhantomReference` 队列 / `Runtime.exec` / `Thread.currentThread().getStackTrace` / `duplicate().position(...).limit(...).slice()`） | 逐个记录；是否真有调用以 §9 扫描结果为准 |

**不需要处理（勿再改）**：`Byte.compareUnsigned`、`Integer.remainderUnsigned`（Java 8，API 24 起）、`Math.multiplyExact(int,int)` / `(long,long)`、`Math.floorDiv(int,int)`、`LinkOption.NOFOLLOW_LINKS`（不抛异常，只是对不支持链接的文件系统无效果）。

---

## 4. 先验证再删：脱糖可能已经覆盖的 3 类

对 `String.strip / stripLeading / stripTrailing / isBlank / lines / repeat`、`Collection.toArray(IntFunction)`、`Predicate.not`，**先实测一次**，通过就从 §3 删掉——上一版把这几项都算成了手工活，实际能省不少。

**验证三步（约 5 分钟）**

1. 确认版本：AGP ≥ 7.4，`coreLibraryDesugaring "com.android.tools:desugar_jdk_libs:2.1.x"`。低于此版本直接判“不覆盖”，按 §3 做。
2. 构建后看 dex 有没有被重写：`apkanalyzer dex packages app-debug.apk | grep -F "j$"`（出 `j$` 前缀的 lang / util 类即已重写）；或 `unzip -p app-debug.apk "classes*.dex" | strings | grep -F "j$"`。
3. 在 **API 26 设备 / 模拟器**上跑一条 instrumentation 单测，直接调用 `"  a  ".strip()`、`" a\nb ".lines().count()`、`list.toArray(String[]::new)`：不抛 `NoSuchMethodError` 即覆盖成功。

> 三点提醒：① Robolectric 跑在宿主 JVM 上，这几项永远不抛，**不能当证据**；② 只加 `coreLibraryDesugaring` 依赖、没开 `isCoreLibraryDesugaringEnabled`，脱糖不生效；③ 覆盖判定按**构建产物**算，不按“文档说支持”算。

---

## 5. 需适配：不崩但语义不同（探测一次 + 缓存 + 显式降级）

原则：**启动时在应用私有目录实测一次并缓存**；宁可功能缺失，不许数据损坏；禁止 catch 后静默继续。

| 能力 | 处理方式 | 风险 |
|---|---|---|
| 符号链接 `createSymbolicLink / readSymbolicLink / isSymbolicLink` | 私有目录实测一次并缓存。Android 的 nio 是 OpenJDK Unix provider 移植，在 ext4 / f2fs 私有目录上**可能可用**；不可用时 `supportsSymlinks()` 返回 false，链接 blob 按 mode `120000` 记录，checkout 降级为普通文件并**显式告警** | **禁止硬编码 false**——那会自造“静默把链接写成普通文件”的语义错误 |
| 硬链接 `createLink` | 同探测；不可用时复制文件替代 | 丢失共享 inode 语义，需记录 |
| POSIX 权限视图 | `getFileAttributeView(PosixFileAttributeView.class) != null` 探测；降级用 `File.setReadable / setExecutable` 近似 | 外部存储 chmod 无效、可执行位丢失 |
| `unix:nlink` | 走探测（可能恒为 1） | 影响 `FileSnapshot` 判定 |
| `dos:hidden` / `volume:vsn` | 按不支持处理；`FS.setHidden` 置空操作 | 与 Git 语义无关，风险低 |
| `ATOMIC_MOVE` | `catch (AtomicMoveNotSupportedException)` → 临时文件 + `REPLACE_EXISTING` + flush；配合 lock 文件与校验和 | 降级窗口 crash 会留下半文件，**必须做 crash 注入测试**。涉及文件数**以扫描为准**（上一版“11 个 / FileUtils 5、GC 4 …”相加已超 11，自相矛盾，本轮删除） |
| `Files.probeContentType` | 返回值判 null 兜底 | 无 |
| `FileStore` 属性、时间戳精度、大小写敏感性 | `FS.detect()` 按 Android 固定最优参数，**不靠探测**；`isCaseSensitive()` 固定 false | 探测值在模拟器 / 真机不一致会导致全量重扫 |

---

## 6. 构建配置必须确认的几项

1. **JGit 6.10 是 Java 11 字节码**：JGit 6.0 起运行与编译基线为 Java 11（6.6 起其自身构建需 JDK 17）。模块 `compileOptions` 设 `VERSION_11`、Kotlin `jvmTarget = "11"`；否则 javac 读 class 版本 55 的 jar 会报错。D8/R8 处理 class 55 是既有能力，不是风险项。
2. **脱糖必须成对配置**：只写 `isCoreLibraryDesugaringEnabled = true` 会报 `coreLibraryDesugaring configuration contains no dependencies`；必须同时加 `coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.x")`。
3. **minSdk 26 不需要 multiDex**（原生多 dex 支持从 API 21 起，support-library 方案只服务 ≤20）。
4. **`java.time` 与补齐库的边界（改为可执行验证）**：AGP 在 minSdk ≥ 26 时通常不重写 `java.time`，但受 AGP 版本与依赖影响，存在仍生成补齐类的案例。验证方式：`apkanalyzer dex packages` 里是否出现同时含 `j$.time` 包；若出现，重点检查与平台方法的交接处（`FileTime.toInstant()` 返回的是平台 `Instant`）、反射与序列化 / JSON 场景，必要时改用不触发重写的用法或调整脱糖配置。**不要预设它一定发生，也不要预设它一定不发生。**
5. **旧 time backport 冲突**：若仍带 ThreeTenABP / ThreeTenBP，与脱糖并发会重复类，必须移除。
6. **仓库位置**：`getFilesDir()` / `getExternalFilesDir()` 私有目录，不指向公共外部存储（walk、随机读写、rename 原子性都不保证）。

---

## 7. 编译期闸门（让“漏改”在 CI 暴露）

- **animal-sniffer（主力）**：signature 指向 **API 26**（不是 compileSdk）；必须打开依赖扫描（`ignoreDependencies=false`，或 `includeDependencies` 加 `org.eclipse.jgit:org.eclipse.jgit`），否则抓不到 JGit jar 内部的 `List.of`；刻意保留的高版本调用用 `@IgnoreJRERequirement` 白名单，不关整条规则。
- **animal-sniffer 与脱糖的交互（上一版缺）**：脱糖在 D8 dex 阶段，animal-sniffer 扫的是 javac 产物，**所以脱糖已覆盖的 `String.strip` 等调用仍会被报成 signature error**。处置：用 `@IgnoreJRERequirement`（或插件 `ignores` / `excludePackages`）按项白名单，注释里写明“已由 desugar_jdk_libs 2.x 覆盖，见 §4 验证记录”；否则闸门长期红着，最后会被整体关掉。
- **Android Lint**：`NewApi` + `InlinedApi` + `ObsoleteSdkInt`，`abortOnError true`。`NewApi` 能报 `List.of`（平台到 API 30 才补）、`String.strip`（API 33）这类“平台后来补上的 java.* API”；`ObsoleteSdkInt` 用来清 minSdk 24 时代遗留的 `SDK_INT >= 24` 死分支。
- **javac 拦不住要写进团队约定**：compileSdk 34 的 `android.jar` 里含相当多 `java.*` 方法的 stub，`List.of`、`strip` 都能编译过。**能编译过 ≠ API 26 上有**，以 animal-sniffer 结果为准。
- **扫描天然早于脱糖**：animal-sniffer 挂在 `verify` / javac 输出上，早于 D8 dex，无需特殊配置；别把它挂到 dex 之后。

---

## 8. 验收清单（全部通过才算“能在 minSdk 26 运行”）

1. animal-sniffer（含 JGit 依赖扫描）零未白名单的 signature error。
2. Lint `NewApi` / `InlinedApi` 零 error；`ObsoleteSdkInt` 死分支已清理。
3. **API 26 真机**跑通 init / clone / checkout / branch / commit / pack / gc 全链路，无 `NoSuchMethodError` / `NoClassDefFoundError` / `UnsupportedOperationException`。
4. 符号链接、硬链接、POSIX 视图、`unix:nlink` 的探测结果来自**私有目录实测**，代码中无硬编码 false。
5. ATOMIC_MOVE 降级路径有 crash 注入测试：中途 kill 后仓库可恢复、无半截 pack。
6. `List.of` / `Set.of` / `Map.of` 替换后，原本会抛 NPE / IAE 的输入路径有单测。
7. `requireNonNull(T,Supplier)` 替换有“supplier 未被调用”的惰性单测。
8. §4 验证记录留档（哪几项确认由脱糖覆盖、用的什么 AGP / desugar_jdk_libs 版本），否则升级依赖后会悄悄回退。
9. 若 §3 #6 已落地：`lines()` 替换涉及 `\u0085` / `\u2028` / `\u2029` 的用例有单测。
10. `readAllBytes` 替换后，100MB 以上 pack 读取无 OOM（改流式）。
11. **Robolectric 不作为兼容性结论**（宿主 JVM 上 `java.*` 是真实 JDK，永远不抛，会给出假绿灯）。

---

## 9. 待扫描确认项（不要凭感觉估数）

用 animal-sniffer 对 **JGit 6.10 jar** 做一次实际扫描，把下表填成真实数字，再替换 §3 各行里的“若干”。上一版的“90–100 处 / 50–60 个文件”是估算，**不可作为工作量承诺**。

| 待确认 | 为什么必须扫 |
|---|---|
| `Path.of` / `Files.readString` / `Files.writeString` | 最容易被“nio 在 API 26 可用”这句话带过去 |
| `Stream.toList` / `Collectors.toUnmodifiable*` | JGit 6.x 大量使用流式收集 |
| `Optional.or / ifPresentOrElse / stream / orElseThrow()` | 决定 §3 #12–#13 是否真要改 |
| `ProcessHandle` / `StackWalker` / `VarHandle` / `Cleaner` | 桌面 JVM 常见、Android 上完全没有 |
| `ByteArrayOutputStream.toString(Charset)` | 低层 IO 代码高频出现 |
| `Thread.onSpinWait` | 藏在自旋等待里，静态检索容易漏 |
| `String.strip / lines / repeat / isBlank` 的**实际处数** | 处数决定 §4 验证的优先级 |
| ATOMIC_MOVE 的实际文件清单 | 决定 crash 注入测试的覆盖面 |

---

**收口一句**：minSdk 26 把“`java.nio.file` 在不在”这一整块风险清零（顺带拿到 `invoke-custom` 原生支持），这是它真正的价值；但 Java 9+ 的集合工厂、`Stream.toList`、`java.io` 的新方法、`Objects.*` 的新重载、`Math` 的新重载、nio 的 Java 11 新方法，脱糖与 API 26 都补不上，必须改源码。先按 §9 扫出真实清单，再用 §4 把脱糖已覆盖的那 3 类划掉，最后靠 animal-sniffer + Lint + API 26 真机三层卡住——这样才不会出现“构建全绿、线上 `NoSuchMethodError`”。
