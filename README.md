# AR眼镜项目结构

## 简介:
AR眼镜项目结构主要由六部分组成，分别是 语音应用(aiMaster)、语音交互UI应用(aiMasterUI)、Launcher应用(Launcher)、媒体应用(glassMedia)、导航应用(glassNavi)、服务应用(glassSysService).

### 1.语音应用（aiMaster）
aiMaster负责组装语音功能和装配子模块，其中子模块包括通用(aiComm)、媒体(aiMedia)、导航(aiNavi)、电话(aiPhone)。
### 2.语音交互UI应用（aiMasterUI）
aiMasterUI负责语音交互界面的展示，设计独立语音交互UI应用是为了方便语音应用不依赖界面可以独立使用。
### 3.Launcher应用（Launcher）
Launcher负责眼镜开机Launcher相关界面，主要包括首页效果、设置界面、升级等功能。
### 4.媒体应用（glassMedia）
glassMedia负责眼镜媒体相关功能，主要包括QQMusic手机版(Qplay)、QQMusic车机版(盲人模式)、喜马拉雅(Xmly使用API)，可以实现播放音乐、点播喜马拉雅电台等功能。
### 5.导航应用（glassNavi）
glassNavi负责眼镜导航相关功能，主要包括导航路线引导展示、剩余距离和时间展示、车道图等。
### 5.服务应用（glassSysService）
glassSysService主要抽象公用的系统服务，主要包括蓝牙电话、日志服务、数据链路服务等。


## 框架图:
![Image text](https://github.com/jiangfw/SocketConnect/blob/master/AR%E7%9C%BC%E9%95%9C%E9%A1%B9%E7%9B%AE%E7%BB%93%E6%9E%84.png)
