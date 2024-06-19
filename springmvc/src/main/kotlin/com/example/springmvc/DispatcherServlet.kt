package com.example.springmvc

import java.lang.reflect.Method
import java.util.*
import javax.servlet.annotation.WebInitParam
import javax.servlet.annotation.WebServlet
import javax.servlet.http.HttpServlet
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

@WebServlet(
    "/*",
    loadOnStartup = 1, //启动tomcat时初始化DispatcherServlet
    initParams = [WebInitParam(name = "contextConfigLocation", value = "application.properties")],
)
class DispatcherServlet : HttpServlet() {

    private lateinit var webApplicationContext: WebApplicationContext // 持有ioc容器，上下文配置信息

    private val handlerMappings = ArrayList<HandlerMapping>() // 存放route对应的相关处理method，遍历集合，按照path匹配

    /**
     * 关键方法，Get和Post请求都会走这个方法去处理Request请求
     */
    private fun doDispatch(req: HttpServletRequest, resp: HttpServletResponse) {

        val handlerMapping = this.getHandler(req)
        if (handlerMapping == null) {
            resp.writer.write("404 Not Found!!!")
            return
        }

        val paramValues = handleMethodParams(handlerMapping, req, resp)

        val returnValue = handlerMapping.method.invoke(handlerMapping.controller, *paramValues)

        if (returnValue == null || returnValue is Unit) return

        resp.writer.write(returnValue.toString()) // 向客户端发送数据

    }

    /**
     * eg.:
     * fun getUserByName(
     *         @RequestParam("username") username: String, @RequestParam("age") age: Int, req: HttpServletRequest,
     *     ): String
     * [HandlerMapping.paramIndexMapping]: ["username" to 0, "age" to 1, "javax.servlet.http.HttpServletRequest" to 2]
     *
     * @return paramValues = ["laurie", 11, req]
     */
    private fun handleMethodParams(
        handlerMapping: HandlerMapping,
        req: HttpServletRequest,
        resp: HttpServletResponse
    ): Array<Any?> {
        val paramTypes = handlerMapping.paramTypes
        val paramValues = arrayOfNulls<Any>(paramTypes.size)

        val paramsMap = req.parameterMap // path中的参数 eg. name=laurie&age=11

        // 方法参数中是否包含 HttpServletRequest，如果有，根据其位置存入paramValues中
        if (handlerMapping.paramIndexMapping.containsKey(HttpServletRequest::class.java.name)) {
            val reqIndex = handlerMapping.paramIndexMapping[HttpServletRequest::class.java.name]!!
            paramValues[reqIndex] = req
        }

        if (handlerMapping.paramIndexMapping.containsKey(HttpServletResponse::class.java.name)) {
            val respIndex = handlerMapping.paramIndexMapping[HttpServletResponse::class.java.name]!!
            paramValues[respIndex] = resp
        }

        // 处理参数在方法中位置和其对应的类型
        for (param in paramsMap) {
            if (!handlerMapping.paramIndexMapping.containsKey(param.key)) continue

            val index = handlerMapping.paramIndexMapping[param.key]!!

            // 请求参数可能是数组：GET /api/users?ids=1,2,3
            val value = Arrays.toString(param.value)
                .replace("\\[|\\]".toRegex(), "")

            paramValues[index] = convert(paramTypes[index], value)
        }
        return paramValues
    }

    /**
     * 根据路由匹配HandlerMapping。
     *
     * 遍历handlerMappings集合，根据Request的path(去除contextPath)匹配HandlerMapping中的url，
     * 匹配不到返回null
     */
    private fun getHandler(req: HttpServletRequest): HandlerMapping? {
        var url = req.requestURI
        val contextPath = req.contextPath
        url = url.replace(contextPath, "").replace("/+".toRegex(), "/")
        for (mapping in handlerMappings) {
            val find: MatchResult? = mapping.url.find(url)
            find?.let { return mapping }
        }
        return null
    }

    /**
     * 只处理了几个简单的基本类型转换
     */
    private fun convert(type: Class<*>, value: String): Any {
        if (Int::class.java == type) {
            return value.toInt()
        } else if (Double::class.java == type) {
            return value.toDouble()
        }
        return value
    }

    /**
     * Tomcat服务器启动时会自动调用这个方法
     * 初始化ioc容器，处理Route与HandlerMapping的映射
     */
    override fun init() {
        webApplicationContext = WebApplicationContext(servletConfig.getInitParameter("contextConfigLocation"))

        initHandlerMapping(webApplicationContext.ioc)
    }

    /**
     * Controller的path加上Method上的path eg. /user/hello
     *
     * url          Controller      Method
     * /user/hello  UserController  sayHello()
     */
    private fun initHandlerMapping(ioc: Map<String, Any>) {
        for (bean in ioc.values) {
            val clazz = bean.javaClass
            if (!clazz.isAnnotationPresent(Controller::class.java)) return

            var baseUrl = ""
            if (clazz.isAnnotationPresent(RequestMapping::class.java)) {
                val requestMapping = clazz.getAnnotation(RequestMapping::class.java)
                baseUrl = requestMapping.path
            }

            for (method in clazz.methods) {
                if (!method.isAnnotationPresent(RequestMapping::class.java)) continue

                val requestMapping = method.getAnnotation(RequestMapping::class.java)
                val regex = ("/" + baseUrl + "/" + requestMapping.path).replace("/+".toRegex(), "/").toRegex()

                handlerMappings.add(HandlerMapping(regex, bean, method))
            }

        }

    }

    /**
     * 有GET请求时tomcat会自动调用这个方法
     */
    override fun doGet(req: HttpServletRequest, resp: HttpServletResponse) {
        this.doPost(req, resp)
    }

    override fun doPost(req: HttpServletRequest, resp: HttpServletResponse) {
        this.doDispatch(req, resp);
    }

    /**
     * url         controller     method
     * /user/hello UserController getUserByName
     */
    class HandlerMapping(
        val url: Regex,
        val controller: Any,
        val method: Method
    ) {
        val paramTypes: Array<Class<*>> = method.parameterTypes
        val paramIndexMapping = HashMap<String, Int>()

        init {
            putParamIndexMapping(method)
        }

        /**
         * 处理方法参数的位置信息
         */
        private fun putParamIndexMapping(method: Method) {
            val pa = method.parameterAnnotations
            for (i in 0 until pa.size) {
                for (a in pa[i]) { // 一个参数变量可能被多个注解修饰
                    if (a is RequestParam) {
                        val paramName = a.name.trim() // eg. RequestParam(name = "username")
                        if (paramName.isNotEmpty()) {
                            paramIndexMapping[paramName] = i // 用户指定的变量名称当做key
                        }
                    }
                }
            }

            /**
             * 处理方法中的HttpServletRequest和HttpServletResponse ，按照类型的名称当作key，在方法参数中的位置索引为value
             */
            val paramTypes = method.parameterTypes
            for (i in 0 until paramTypes.size) {
                val type = paramTypes[i]
                if (type == HttpServletRequest::class.java || type == HttpServletResponse::class.java) {
                    paramIndexMapping[type.name] = i
                }
            }
        }

    }
}











