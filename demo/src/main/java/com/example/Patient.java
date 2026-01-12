package com.example;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Patient {
    private String name;
    private int age;
    private String token;
    public Patient() {
    }

    public Patient(String name, int age, String token) {
        this.name = name;
        this.age = age;
        this.token = token;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public int getAge() { return age; }
    public void setAge(int age) { this.age = age; }

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }

    @Override
    public String toString() {
        return "Token: " + token + ", Name: " + name + ", Age: " + age;
    }
}