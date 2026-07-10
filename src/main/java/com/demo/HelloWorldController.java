package com.demo;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller that serves the Hello World web page.
 */
@RestController
public class HelloWorldController {

    /**
     * Returns a simple Hello World HTML page.
     *
     * @return HTML string rendered by the browser
     */
    @GetMapping(value = "/", produces = MediaType.TEXT_HTML_VALUE)
    public String helloWorld() {
        return "<!DOCTYPE html>"
            + "<html lang=\"en\">"
            + "<head><meta charset=\"UTF-8\"><title>Hello World</title>"
            + "<style>"
            + "body{font-family:Arial,sans-serif;display:flex;justify-content:center;"
            + "align-items:center;height:100vh;margin:0;background:#f0f4f8;}"
            + ".card{background:#fff;padding:2rem 3rem;border-radius:12px;"
            + "box-shadow:0 4px 20px rgba(0,0,0,0.1);text-align:center;}"
            + "h1{color:#2d3748;font-size:2.5rem;margin-bottom:0.5rem;}"
            + "p{color:#718096;font-size:1rem;}"
            + "</style></head>"
            + "<body><div class=\"card\">"
            + "<h1>Hello World!</h1>"
            + "<p>Spring Boot application deployed via GitHub Actions CI/CD pipeline.</p>"
            + "</div></body></html>";
    }

    /**
     * Health check endpoint.
     *
     * @return plain text status
     */
    @GetMapping(value = "/health", produces = MediaType.TEXT_PLAIN_VALUE)
    public String health() {
        return "OK";
    }
}
