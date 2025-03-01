# MGit
![GitHub Downloads (all assets, all releases)](https://img.shields.io/github/downloads/Zacharia2/MGit/total)

MGit是一款Git客户端安卓应用。（Version ≥ Android 12）。拥有常用的拉取、推送、提交、合并、克隆、比较、衍合、择取、创建新的分支、添加本地仓库、重置更改等等功能。当前维护者为 maks/MGit，此分支的设计意义和目标是填补Android平台上对Git知识库同步功能的空白。

## 快速开始

### 使用多远程同步

增加功能：同步、撤销提交、Tokens设置、默认仓库目录，并所有依赖的软件包进行升级。修复暂存所有文件过慢问题、修复inlater has been closed导致的解压缩问题、修复 avatar 全球通用头像、修复仓库列表打开远程地址问题、修改提交命令中的暂存所有已跟踪的文件选项为暂存所有文件选项，减少一步暂存所有文件的步骤，以及优化一些使用体验等等。

MGit+Gitee用来同步obsidian、logseq等笔记库非常迅速且附加历史版本，可以随时回滚查看历史记录。国内推荐使用Gitee作为同步使用的默认远程仓库；用Github作为工作和备份用的远程仓库，可定时用桌面端Git命令进行同步。在桌面端使用git remote add添加多远程仓库Gitee和Github。移动端只使用默认默认的Gitee远程仓库，安装并打开MGit，点击顶部导航栏右侧菜单中的设置，配置Git用户名和用户邮箱。若你的仓库是私有的，则需要配置token账户和token秘钥。Token在Gitee中点击`用户头像-设置-令牌`，然后点击生成令牌。此令牌专用于访问你的私有库。设置好后，点击导航栏最右侧菜单、点击克隆、填入克隆地址，等待克隆完成。你也可以选择导入，在导入时勾选导入为外部仓库。外部仓库和内部仓库的区别在于是否存储在软件自己的内部存储私有目录中，内部存储私有目录一般可以看做：`data/data/package_name`、`android/data/package_name`。

![image](https://github.com/user-attachments/assets/e90b58aa-e368-4939-ba3e-254c0bcebd8e)

在仓库列表中，点击仓库可以进入仓库详情页，在详情页可以左右滑动，从左到右分别有三个页面依次为：文件、提交、状态。可以在文件查看仓库中的文件，在提交可以查看提交的历史，在状态可以查看修改。一般使用状态查看修改状态，点击未暂存DIFF可以看到有没有修改的还没暂存的文件。我通常会使用仓库详情页中导航栏中最右侧的菜单按钮，打开后点击同步按钮，然后直接按照同步策略进行同步。现在好好享用安卓版本的快速同步功能吧！

### 克隆远程仓库

1. 点击`+`图标添加新仓库
2. 输入远程URL（见下面的URL格式）
3. 输入本地仓库名称 - 注意这**不是**完整路径，因为MGit将所有仓库存储在同一个本地目录中（可以在MGit设置中更改）
4. 点击`Clone`按钮
5. 如果需要，系统会提示您输入连接到远程仓库的凭据。MGit会将仓库（所有分支）下载到您的设备上

### 创建本地仓库

1. 点击`+`图标添加新仓库
2. 点击`Init Local`创建本地仓库
3. 当提示时，输入该仓库的名称
4. 将创建一个本地的空仓库

### URL格式

#### SSH URL

* 运行在标准端口（22）的SSH：`ssh://username@server_name/path/to/repo`
* 运行在非标准端口的SSH：`ssh://username@server_name:port/path/to/repo`
* 需要填写`username` - 默认情况下，MGit会尝试以root身份连接。

#### HTTP(S) URL

* HTTP(S) URL：`https://server_name/path/to/repo`

# 许可证

请参阅 [GPLv3](./LICENSE) 由`maks@manichord.com`编写的所有代码，您可以选择也可以在[MIT许可证](https://en.wikipedia.org/wiki/MIT_License)下使用。

