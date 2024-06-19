package com.example.springmvc

import java.io.File
import java.util.*

class WebApplicationContext(contextConfigLocation: String) {
    private val contextConfig = Properties() // 配置文件信息，-application.properties

    private val classNameList = ArrayList<String>() // 类的名称全称集合

    val ioc = HashMap<String, Any>() // key: 小写化的[Class].simpleName, value: Controller, Service 实例

    init {
        doLoadConfig(contextConfigLocation) // 加载配置信息 [contextConfigLocation] 为 application.properties

        doScanner(contextConfig.getProperty("scanPackage"))  // scanPackage=com.example.springmvc，扫描类的名称加入classNameList中

        doInstance() // 实例化注入ioc容器中

        doAutowired() // 依赖注入
    }

    /**
     * 加载配置信息 [contextConfigLocation] 为 application.properties
     * [Properties]按照key=value的形式解析文件。
     * eg.
     * name=laurie
     * color=red
     * scanPackage=com.example.springmvc
     *
     * 文件名一般以properties为后缀，例如：application.properties
     * 通过[Properties].getProperty("name")获取对应值
     */
    private fun doLoadConfig(contextConfigLocation: String) {
        val inputStream = this.javaClass.classLoader.getResourceAsStream(contextConfigLocation)
        inputStream?.use { contextConfig.load(it) }
    }

    /**
     * 递归遍历classes文件夹，将类的全称放入[classNameList]中，用于之后的根据类的名称进行实例化
     *
     * classLoader.getResource("") file:/D:/xx/xx/simple-springmvc/out/artifacts/springmvc_war_exploded/WEB-INF/classes/
     * classLoader.getResource("/") file:/D:/xx/xx/simple-springmvc/out/artifacts/springmvc_war_exploded/WEB-INF/classes/
     * [classPath] D:\xx\xx\simple-springmvc\out\artifacts\springmvc_war_exploded\WEB-INF\classes\com\example\springmvc
     *
     * 目录结构大致如下：
     * - classes/
     *  - com/example/springmvc/
     *      - controller/
     *       - UserController.class
     *      - service/
     *       - UserService.class
     *      - DispatcherServlet.class
     *      - WebApplicationContext.class
     *  - META-INF/
     *  - application.properties
     */
    private fun doScanner(scanPackage: String) {
        val url = this.javaClass.classLoader.getResource(scanPackage.replace(".", "\\"))
        val classPath = File(url!!.file)
        for (file in classPath.listFiles()!!) {
            if (file.isDirectory) {
                doScanner(scanPackage + "." + file.name)
                continue
            }
            if (!file.name.endsWith(".class")) continue
            val className = scanPackage + "." + file.name.replace(".class", "")
            classNameList.add(className)
        }
    }

    /**
     * 根据Class的名称全称实例化，并放入ioc容器中，类的小写化simpleName作为key
     *
     * 只实例化了带有Controller和Service的类
     */
    private fun doInstance() {
        for (className in classNameList) {
            val clazz = Class.forName(className)
            if (clazz.isAnnotationPresent(Controller::class.java)) {
                val constructor = clazz.getDeclaredConstructor()
                constructor.isAccessible = true
                val instance = constructor.newInstance()
                val beanName = clazz.simpleName.lowercase()
                ioc[beanName] = instance
            } else if (clazz.isAnnotationPresent(Service::class.java)) {
                val beanName = clazz.simpleName.lowercase()
                ioc[beanName] = clazz.newInstance()
            } else continue
        }
    }

    /**
     * 根据字段的类型的小写化的simpleName或者自定义名称匹配ioc容器中的bean，实例化字段
     *
     * 可访问标志表示是否屏蔽Java语言的访问检查，默认值是false
     * 不管是什么访问权限，其可访问标志的值都为false, 即public默认也是false,
     * 关闭访问检查可以加快反射的运行速度
     *
     * From chatgpt:
     * 对于public字段，反射访问时不会报错，不需要关闭访问检查。而对于非public字段（例如private字段），
     * 则需要使用setAccessible(true)来关闭访问检查，否则会抛出IllegalAccessException。
     *
     * declaredFields: 获取某个类的自身的所有字段，不包括父类的字段
     * fields: 获取某个类的所有的public字段，其中是包括父类的public字段的
     */
    private fun doAutowired() {
        for (bean in ioc.values) {
            val fields = bean.javaClass.declaredFields
            for (field in fields) {
                if (!field.isAnnotationPresent(AutoWired::class.java)) return
                val autowired = field.getAnnotation(AutoWired::class.java)
                var beanName = autowired.name.trim()
                if (beanName.isEmpty()) {
                    beanName = field.type.simpleName.lowercase()
                }
                field.isAccessible = true // 关闭访问检查
                field.set(bean, ioc[beanName])
            }
        }

    }
}
