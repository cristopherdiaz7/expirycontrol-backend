package com.example.demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
//importa herramientas para arrancar la aplicacion

@SpringBootApplication
public class DemoApplication {
//clase principal del proyecto

	public static void main(String[] args) {
		System.setProperty("user.timezone", "UTC");
		SpringApplication.run(DemoApplication.class, args);
	}

}
