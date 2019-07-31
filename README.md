
## Moquette的改进

[![Build Status](https://api.travis-ci.org/andsel/moquette.svg?branch=master)](https://github.com/irubant/moquette)

* 对moquette的测试上改进了众多功能。

## 改进

* 修改消息队列长度为32，避免了原来消息队列超过最大条数之后，publish出错的情况
* 修改了storage的构造函数，使其更通用
* 修改了每次都对clientId的判定，针对client首次链接的情况
* 修改了离线消息签收时的空指针异常
* 弃用了一些不常用的模块
* 添加了redis存储实现
		redis采用了现有conf配置机制
		重新设计了session的存储结构，以便后续添加分片处理
* 修改工程的结构，独立了common模块，同时将redis，mapdb，broker建在common基础上
* 针对publish的内存泄漏，进行了修改，moquette未回收导致


<p>经常会在项目中用到消息传递，在不同的场景下，消息传递的要求是不一样的。java世界中，jms的规范可遵循，同时也有开源的相关软件来支持。
本文来说说一下mqtt，以及moquette。在选择mqtt的中间件时较为纠结，对于非大众化的开源框架的使用没有底气。好在有源码，研究源码，经过大量测试，效果还可以，推荐给大家。</p>
 
 经测试过程发现moquette存在一些问题，已修改，可能是认识的问题，也可能是出发点不一样。
 
 总之，修改如下：
 1. 修改消息队列长度为32，避免了原来消息队列超过最大条数之后，publish出错的情况 修改了storage的构造函数，使其更通用
 2. 修改了每次都对clientId的判定，针对client首次链接的情况 修改了离线消息签收时的空指针异常 弃用了一些不常用的模块
 3. 添加了redis存储实现 redis采用了现有conf配置机制 重新设计了session的存储结构，以便后续添加分片处理
 4. 修改工程的结构，独立了common模块，同时将redis，mapdb，broker建在common基础上
 5. 针对publish的内存泄漏，进行了修改（原本以为是netty泄露），经过两天的不眠不休的调试，发现是moquette未回收导致
 
使用说明文档：

<font color="blue">1.简介</color>
====

Moquette是一款开源的消息代理，整个系统基于java开发，以netty为基础完整实现了MQTT协议的。
基于测试，moquette的客户端承载量及消息的推送速度都比较客观，在大批量频繁短线上线的情景下，也可以承受。
Moquette代码是完全开源的，测试过程中的问题进行了一定的修改，扩展实现了基于redis存储的机制。

<font color="blue">2.使用<font>
====

2.1配置文件
-------

Moquette所使用的配置文件位于其根目录下的config里，包括以下：

 1. acl.conf 权限配置 
 2. hazelcast.xml  集群配置
 3. password_file.conf 用户密码配置
 4. moquette.conf 主配置

下面将详细讲解各配置文件

### 权限配置

基于文件的权限配置较为复杂，以下为示例格式，将针对该示例具体说明。

```
user admin
topic write mqtt/log
pattern write mqtt/log/+
topic read mqtt/lost
user client
topic read mqtt/log
pattern read mqtt/log/%c
topic write mqtt/lost
```

 1. [user admin]  指示一个用户admin。其后的条目，代表该用户的相关topic的读写权限，一直到另一个user结束。
   
 2. [topic write wifi/log] 代表队wifi/log主题具有write权限，topic命令指定特定的主题名称，不能带有通配符。
  
 3. [pattern write wifi/log/+] 使用通配符指示符合规则的一定数量的topic的权限。
       权限分类：
       <ul>
	       <li>write</li>
	      <li> read</li>
	      <li>writeread</li>
       </ul>
### 集群配置

Moquette的集群配置实用的是hazelcast。Hazelcast是基于java编写的数据同步工具。在moquette中，用于不同节点消息的同步。

```
<network>
<public-address>IP1:5701</public-address>
<port>5701</port>
<join>
       <multicast enabled="false" />
       <tcp-ip enabled="true">
              <required-member>IP2:5701</required-member>
       </tcp-ip>
</join>
</network>
```
public-address:代表了当前节点的IP及端口
required-member：代表了集群中的其他节点。
各节点的集群模式建立后，各节点是对等关系，无主从之分
 
### 用户管理
该文件用于系统的可登录用户，实例格式如下：

```
#*********************************************
# Each line define a user login in the format
#   <username>:sha256(<password>)
#*********************************************
#NB this password is sha256(passwd)
admin:8d969eef6ecad3c29a3a629280e686cf0c3f5d5a86aff3ca12020c923adc6c92
client:8d969eef6ecad3c29a3a629280e686cf0c3f5d5a86aff3ca12020c923adc6c92
```
该文件的格式非常简单：
每行代表了一个用户及其密码，用:分割，密码是sha256摘要后的结果。

关于client(消费者)所使用的用户，大部分情况下，client只需要其clientId来区分，因此后台可针对业务类型建立不同的用户分给client使用，不需要为每个clientId都建立用户。

### 主配置
主配置中包含了较多内容，介绍如下：
 
#### 1.端口

```
port 1883
websocket_port 8383
```
port 1883 是broker的主端口，默认为MQTT协议的1883端口
由于系统提供了websocket功能，可以使用websocket的方式使用（该模式未进行测试）。

#### 2. SSL端口及配置

```
# ssl_port 8883
#jks_path serverkeystore.jks
#key_store_password passw0rdsrv
#key_manager_password passw0rdsrv
```
对于有较高安全要求的系统，可以添加SSL支持。

#### 3.IP绑定限制

```
#*********************************************************************
# The interface to bind the server
#  0.0.0.0 means "any"
#*********************************************************************
host 0.0.0.0
```
#### 4.存储设置
storage_class io.moquette.persistence.redis.RedisStorageService
由于基于不同存储的实现的性能，差异性较大，moquette默认采用内存存储的模式，该模式有很高的性能，但存在单点崩溃下，消息丢失的风险（由于集群负载的使用，可降低该问题发生的影响范围）。
如果对存储过于看重，性能可求次，可使用基于redis的存储实现，其自带的mapdb的存储实现，错误较多。
在不设置的情况，默认采用的基于memory的存储实现。

#### 5.启用权限访问

```
#*********************************************************************
# acl_file:
#    defines the path to the ACL file relative to moquette home dir
#    contained in the moquette.path system property
#*********************************************************************
acl_file config/acl.conf
```
以上代表了，broker将以acl.conf中的内容为基础进行授权鉴权。

#### 6.是否允许匿名访问

```
#*********************************************************************
# allow_anonymous is used to accept MQTT connections also from not
# authenticated clients.
#   - false to accept ONLY client connetions with credentials.
#   - true to accept client connection without credentails, validating
#       only against the password_file, the ones that provides.
#*********************************************************************
allow_anonymous false
```
以上代表不允许匿名访问，必须使用用户名及密码才可以访问。

#### 7.用户密码文件配置

```
#*********************************************************************
# password_file:
#    defines the path to the file that contains the credentials for
#    authenticated client connection. It's relative to moquette home dir
#    defined by the system property moquette.path
#*********************************************************************
password_file config/password_file.conf
```
以上代表broker将使用password文件进行鉴权，若不需要则可以将其注释掉。

#### 8.epoll的启用

```
#*********************************************************************
# Netty Configuration
#*********************************************************************
#
# Linux systems can use epoll instead of nio. To get a performance
# gain and reduced GC.
# http://netty.io/wiki/native-transports.html for more information
#
netty.epoll true
```
在linux系统下，提供的epoll机制，可使系统能够承载更高的终端。以上代表启用epoll。在机器硬件较好的情况下，epoll模式提升明显。

#### 9.集群配置

```
#hazelcast
#intercept.handler io.moquette.interception.HazelcastInterceptHandler
```
集群配置的情况下，需要开启以上配置，开启配置的前提是hazelcast.xml文件已配置。

#### 10.Redis配置

```
#redis storage
redis.host localhost
redis.port 6379
redis.password
redis.database 0
redis.prefix monitor:
```
在store_class已经配置为redis的情况下，需要配置以上参数，由于集群模式使用hazelcast，目前的基于redis的实现，不具备分片等功能，但键值的设计已经具备。

### 2.2 启动
Moquette代码工程采用maven管理，采用maven install可以打包一个在linux下运行的文件，打包后的格式如下：
 
- Lib目录是所有使用到的lib文件，分为：
![这里写图片描述](http://img.blog.csdn.net/20170916141411834?watermark/2/text/aHR0cDovL2Jsb2cuY3Nkbi5uZXQvd2FuZ2luMTAxMw==/font/5a6L5L2T/fontsize/400/fill/I0JBQkFCMA==/dissolve/70/gravity/SouthEast)

1. Netty相关
1. Hazelcast
1. Log相关
1. Redis存储实现引入的lib：在使用memory的模式下，redis相关可以删除，减少包的大小。

- Bin目录：
![这里写图片描述](http://img.blog.csdn.net/20170916141506667?watermark/2/text/aHR0cDovL2Jsb2cuY3Nkbi5uZXQvd2FuZ2luMTAxMw==/font/5a6L5L2T/fontsize/400/fill/I0JBQkFCMA==/dissolve/70/gravity/SouthEast)
      
- linux下的moquette.sh启动方式：
       默认不是以后台运行的方式，需要使用以下命令运行：

linux下的moquette.sh启动方式：
       默认不是以后台运行的方式，需要使用以下命令运行：
       
```
 setsid  ./moquette.sh &
```
nohup命令模式会找不到配置好的log输出。
Windows下以bat命令运行
## 2.3 客户端
<p>实现moqtt协议的客户端存在很多种，针对该broker，目前测试使用的是eclipse-phao的，该客户端实现提供了多种语言版本，便于不同终端使用。</p>

可以在以下的网址中找到相关语言版本的下载：http://www.eclipse.org/paho/downloads.php

<p>针对不同的语言版本，可提供的功能存在不同，目前broker默认没有实现除mqtt协议规范中提到的功能。</p>

<p>重连机制，需要客户端想法实现该机制，避免客户端掉线后只能重启才能链接的境界。</p>

![image](http://dl2.iteye.com/upload/attachment/0127/0076/3a199972-f037-350e-91e6-424123706877.png)

# <font color="blue">3. 测试</font>
该broker经过了多次测试。
测试场景：

#### 1.机器配置
内存 | CPU
---|---
8G | 4核

Client所运行机器多样，每台机器运行5000个client。
Publish为普通windows机器，两个publisher，每个5个发送线程，平均每秒100条消息。

#### 2.消息发送速度

- 10秒一条群发的情况下，测试3论。
- 每秒100条点对点的消息情况下，测试3轮。
- 每轮测试20到30几个小时。

#### 3.客户端情况
Client在clean Session的情况下，broker的内存占用较低，仅400M左右
在不清理会话的情况下，内存占用较高，在client大批量反复掉线重连情况下内存占用达到2G
心跳设置60s，过短(低于30s)的心跳，对broker来说不能承受。

#### 4集群情况
搭建了两个节点的集群，通过nginx进行tcp负载，客户端测试数量为3万。

## 3.1承载量测试
1.单broker从8000，15000，18000，25000几个级别的测试，在不发送消息的情况下，这几个级别的客户端都可以连接。


承载量 | 结果
---|---
8000 | OK
15000 | OK
18000 | OK
25000 | OK

2.在发送消息的情况

承载量 | 结果
---|---
8000 | OK
15000 | OK
18000 | NG
25000 | NG

在每10秒一条群发消息的情况下，单点broker的无掉线承载量是15000，18000发生较多的掉线情况。

## 3.2消息接收速度

在10秒一条群发消息及每秒100条点对点消息的发送情况下，消息的接受速度都在1秒以内。

## 3.3所占内存
基于实现分析，内存的占用主要由client在不清理会话进行链接掉线后产生的消息积累，在原有基于内存的实现机制中，为每个client保存1024条消息，在超过1024条后，消息会导致publish端出错。修改后的实现，为每个离线client保存最新的32条消息，超过32条的将被丢弃。

基于redis的实现，消息目前没有设定弃用或过期机制。

测试期间的内存分析：
IP1的broker，总内存占用情况如下
![image](http://dl2.iteye.com/upload/attachment/0127/0078/8da09560-9a31-3194-8e93-01c902a17c1a.png)
![image](http://dl2.iteye.com/upload/attachment/0127/0080/8f0131c8-b666-3021-93bd-fe82e83b6e6e.png)

内存稳定在800M左右，处于稳定状态。

## 3.4注意问题
基于内存的存储实现，目前仅保存32条离线消息，超过32条将丢弃原有的。

遗愿消息内容必须为ascii，不能为其他字符。遗愿消息主要用于客户端掉线后的处理。

客户端的心跳不能设置过小，否则broker的承载量将严重下降，建议60s以上。

遗留的问题：

- 在心跳之间的时间段，测试发现存在broker误签收的情况。
- 以上问题，在业务实际使用过程中，采取业务签收等方式，避免消息质量的不可靠性的出现。
