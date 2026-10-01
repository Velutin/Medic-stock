package com.project.mss.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/monitoring")
@Tag(name = "Monitoring", description = "Service monitoring endpoints")
public class MonitoringController {

    private final DiscoveryClient discoveryClient;

    @SuppressWarnings("unused")
    MonitoringController(DiscoveryClient discoveryClient) {
        this.discoveryClient = discoveryClient;
    }

    @GetMapping("/services")
    @Operation(summary = "List available services",
            description = "Returns all services registered in Eureka")
    public ResponseEntity<Map<String, Object>> getAvailableServices() {
        List<String> services = discoveryClient.getServices();
        Map<String, Object> response = new HashMap<>();
        response.put("total_services", services.size());
        response.put("services", services);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/services/details")
    @Operation(summary = "Service details",
            description = "Returns detailed information for all services")
    public ResponseEntity<Map<String, List<ServiceInstance>>> getServicesDetails() {
        List<String> services = discoveryClient.getServices();
        Map<String, List<ServiceInstance>> servicesInfo = new HashMap<>();
        for (String service : services) {
            List<ServiceInstance> instances = discoveryClient.getInstances(service);
            servicesInfo.put(service, instances);
        }
        return ResponseEntity.ok(servicesInfo);
    }

    @GetMapping("/health")
    @Operation(summary = "Service health status",
            description = "Checks whether critical services are available")
    public ResponseEntity<Map<String, Object>> getServicesHealth() {
        Map<String, Object> health = new HashMap<>();
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
        // Status geral
        boolean allServicesUp = !mssInstances.isEmpty() && !mailInstances.isEmpty();
        health.put("overall_status", allServicesUp ? "HEALTHY" : "DEGRADED");
        return ResponseEntity.ok(health);
    }
}
