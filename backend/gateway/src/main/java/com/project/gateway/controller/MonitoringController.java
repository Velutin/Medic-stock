package com.project.gateway.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/monitoring")
public class MonitoringController {

    private final DiscoveryClient discoveryClient;

    public MonitoringController(DiscoveryClient discoveryClient) {
        this.discoveryClient = discoveryClient;
    }

    @GetMapping("/services")
    public Mono<Map<String, Object>> getAvailableServices() {
        return Mono.fromCallable(() -> {
            List<String> services = discoveryClient.getServices();
            Map<String, Object> response = new HashMap<>();
            response.put("total_services", services.size());
            response.put("services", services);
            return response;
        });
    }

    @GetMapping("/services/details")
    public Mono<Map<String, List<ServiceInstance>>> getServicesDetails() {
        return Mono.fromCallable(() -> {
            List<String> services = discoveryClient.getServices();
            Map<String, List<ServiceInstance>> servicesInfo = new HashMap<>();
            for (String service : services) {
                List<ServiceInstance> instances = discoveryClient.getInstances(service);
                servicesInfo.put(service, instances);
            }
            return servicesInfo;
        });
    }

    @GetMapping("/health")
    public Mono<Map<String, Object>> getServicesHealth() {
        return Mono.fromCallable(() -> {
            Map<String, Object> health = new HashMap<>();
            
            // Gateway check (always UP if this code is running)
            health.put("gateway-service", Map.of(
                    "status", "UP",
                    "instances", 1
            ));
            
            // Verificar MSS Service
            List<ServiceInstance> mssInstances = discoveryClient.getInstances("mss");
            health.put("mss-service", Map.of(
                    "status", !mssInstances.isEmpty() ? "UP" : "DOWN",
                    "instances", mssInstances.size()
            ));
            
            // Verificar Mail Service
            List<ServiceInstance> mailInstances = discoveryClient.getInstances("mail");
            health.put("mail-service", Map.of(
                    "status", !mailInstances.isEmpty() ? "UP" : "DOWN",
                    "instances", mailInstances.size()
            ));
            
            // Verificar Eureka Server
            List<ServiceInstance> eurekaInstances = discoveryClient.getInstances("eureka-server");
            health.put("eureka-server", Map.of(
                    "status", !eurekaInstances.isEmpty() ? "UP" : "DOWN",
                    "instances", eurekaInstances.size()
            ));
            
            // Status geral
            boolean allServicesUp = !mssInstances.isEmpty() && !mailInstances.isEmpty();
            health.put("overall_status", allServicesUp ? "HEALTHY" : "DEGRADED");
            
            return health;
        });
    }
}
