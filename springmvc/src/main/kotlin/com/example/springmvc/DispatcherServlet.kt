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

    private lateinit var webApplicationContext: WebApplicationContext

    private val handlerMapping = ArrayList<HandlerMapping>()

    private fun doDispatch(req: HttpServletRequest, resp: HttpServletResponse) {

        val handlerMapping = this.getHandler(req)
        if (handlerMapping == null) {
            resp.writer.write("404 Not Found!!!")
            return
        }

        val paramTypes = handlerMapping.paramTypes
        val paramValues = arrayOfNulls<Any>(paramTypes.size)

        val paramsMap = req.parameterMap

        if (handlerMapping.paramIndexMapping.containsKey(HttpServletRequest::class.java.name)) {
            val reqIndex = handlerMapping.paramIndexMapping[HttpServletRequest::class.java.name]!!
            paramValues[reqIndex] = req
        }

        if (handlerMapping.paramIndexMapping.containsKey(HttpServletResponse::class.java.name)) {
            val respIndex = handlerMapping.paramIndexMapping[HttpServletResponse::class.java.name]!!
            paramValues[respIndex] = resp
        }

        for (param in paramsMap) {
            if (!handlerMapping.paramIndexMapping.containsKey(param.key)) continue

            val index = handlerMapping.paramIndexMapping[param.key]!!

            val value = Arrays.toString(param.value)
                .replace("\\[|\\]".toRegex(), "")

            paramValues[index] = convert(paramTypes[index], value)
        }

        val returnValue = handlerMapping.method.invoke(handlerMapping.controller, *paramValues)

        if (returnValue == null || returnValue is Unit) return

        resp.writer.write(returnValue.toString())

    }

    private fun getHandler(req: HttpServletRequest): HandlerMapping? {
        var url = req.requestURI
        val contextPath = req.contextPath
        url = url.replace(contextPath, "").replace("/+".toRegex(), "/")
        for (mapping in handlerMapping) {
            val find: MatchResult? = mapping.url.find(url)
            find?.let { return mapping }
        }
        return null;
    }

    private fun convert(type: Class<*>, value: String): Any {
        if (Int::class.java == type) {
            return value.toInt()
        } else if (Double::class.java == type) {
            return value.toDouble()
        }
        return value
    }

    override fun init() {
        webApplicationContext = WebApplicationContext(servletConfig.getInitParameter("contextConfigLocation"))

        initHandlerMapping(webApplicationContext.ioc)
    }

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

                handlerMapping.add(HandlerMapping(regex, bean, method))
            }

        }

    }

    override fun doGet(req: HttpServletRequest, resp: HttpServletResponse) {
        this.doPost(req, resp)
    }

    override fun doPost(req: HttpServletRequest, resp: HttpServletResponse) {
        this.doDispatch(req, resp);
    }

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
                        val paramName = a.name.trim()
                        if (paramName.isNotEmpty()) {
                            paramIndexMapping[paramName] = i
                        }
                    }
                }
            }

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











