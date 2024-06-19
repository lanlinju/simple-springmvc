package com.example.springmvc.controller

import com.example.springmvc.AutoWired
import com.example.springmvc.Controller
import com.example.springmvc.RequestMapping
import com.example.springmvc.RequestParam
import com.example.springmvc.service.UserService
import javax.servlet.http.HttpServletRequest

@Controller
@RequestMapping("/user")
class UserController() {

    @AutoWired
    private lateinit var userService: UserService

    @RequestMapping("/name")
    fun getUserByName(
        @RequestParam("username") username: String, @RequestParam("age") age: Int,
        req: HttpServletRequest,
    ): String {
        userService.sayHello()
        return "$username is $age"
    }

}
