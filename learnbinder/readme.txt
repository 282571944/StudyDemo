Android用于进程间通信(IPC)的一种机制
    Binder架构:由服务端接口、Binder驱动、客户端接口三个模块构成(binder_structure.png)。
    包裹:只能写入三种类型-> 1.Java原子类型? 2.Binder引用 3.实现Parcelable对象

    服务端:服务端实际为一个Binder类对象
        1.启动隐藏线程并接受Binder驱动发送的消息
        2.执行服务端Binder的onTransact函数处理消息
        3.onTransact函数的输出依赖客户端Binder的transact函数的输入(所以必须定义客户端和服务端接受的参数的约定)

    Binder驱动:服务端Binder被创建时,Binder驱动会创建一个mRemote对象,该对象也是Binder类.客户端访问服务通过于这个类.
        0.服务端Binder创建时,Binder驱动创建一个mRemote对象
        1.驱动重载transact函数
        2.向服务端发送调用消息
        3.挂起当前线程,等待服务端执行完成后的通知
        4.接到通知后,继续客户端线程,并返回执行结果

    客户端:
        1.获取Binder驱动的mRemote对象.依赖于Service来获取mRemote对象,获取流程为以下流程(/readme/binder_process.png):
            a.启动服务
            b.绑定一个既有服务
            c.获取绑定回调中的服务mRemote对象
        2.执行transact函数
        3.等待结果

Android Studio 自带AIDL命令生成工具作用(/readme/binder_aidl.png):
    1.统一通信服务的写法
    2.统一通信间的存入和读入包裹
    3.返回代理调用

    1.实现Binder作为服务端
    2.实现接口函数处理服务端逻辑
    3.返回mRemote代理处理客户端逻辑



WMS:
    窗口管理的核心问题。1.窗口布局 2.窗口大小 3.窗口聚焦 4.窗口切换
    WMS内部三操作.1.assign layer 2.preform layout 3.place surface
