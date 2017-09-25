
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
