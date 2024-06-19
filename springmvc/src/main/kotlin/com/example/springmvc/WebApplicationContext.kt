package com.example.springmvc

import java.io.File
import java.util.*

class WebApplicationContext(contextConfigLocation: String) {
    private val contextConfig = Properties()

    private val classNameList = ArrayList<String>()

    val ioc = HashMap<String, Any>()

    init {
        doLoadConfig(contextConfigLocation)

        doScanner(contextConfig.getProperty("scanPackage"))

        doInstance()

        doAutowired()
    }

    private fun doLoadConfig(contextConfigLocation: String) {
        val inputStream = this.javaClass.classLoader.getResourceAsStream(contextConfigLocation)
        inputStream?.use { contextConfig.load(it) }
    }

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
