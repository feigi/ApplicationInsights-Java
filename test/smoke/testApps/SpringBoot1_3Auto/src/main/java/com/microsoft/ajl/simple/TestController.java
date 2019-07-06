package com.microsoft.ajl.simple;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TestController {

    @GetMapping("/")
    public String root() {
        return "OK";
    }


    @GetMapping("/test")
    public String test() {
        return "OK!";
    }
}
