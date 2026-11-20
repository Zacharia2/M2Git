# simple-milton

- scoop install git openjdk17 eclipse-java
- fs 包实现了 milton 的接口（copy）
  - FileSystemResourceFactory 是一个 ResourceFactory 接口的实现
  - HttpManagerBuilder 请求一个 ResourceFactory 类型对象用于构建 HttpManager
  - SimpletonServer 请求 HttpManagerBuilder 构建好的 HttpManager，构建一个 webdav2 服务器。
  - 服务器运行，可以在文件资源管理器和浏览器中看到效果。`localhost:8080`
- 运行 SimpleMiltonServer 类即可启动服务器。
