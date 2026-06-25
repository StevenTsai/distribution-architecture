package com.distribution.casestudy.mto.config;

import com.distribution.casestudy.mto.model.WorkOrderProcess;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Process route configuration loaded from YAML.
 *
 * <p>Replaces the jewelry-specific {@code BaseProcessTemplate} with a YAML-driven
 * configuration. Instead of storing process IDs as a comma-separated string in
 * the database, routes are defined in {@code workflow/*.yaml} files.</p>
 *
 * <p>Example YAML:</p>
 * <pre>
 * mto:
 *   routes:
 *     - name: "standard-furniture"
 *       description: "Standard furniture production route"
 *       processes:
 *         - code: "CUT"
 *           name: "Cutting"
 *           sortOrder: 1
 *           plannedHours: 2.0
 *           needInspection: false
 *         - code: "SAND"
 *           name: "Sanding"
 *           sortOrder: 2
 *           plannedHours: 1.5
 * </pre>
 */
@Component
@ConfigurationProperties(prefix = "mto")
public class ProcessRouteConfig {

    private List<RouteDefinition> routes = new ArrayList<>();

    public List<RouteDefinition> getRoutes() { return routes; }
    public void setRoutes(List<RouteDefinition> routes) { this.routes = routes; }

    /**
     * Find a route by name.
     *
     * @param routeName the route name
     * @return the route definition, or null if not found
     */
    public RouteDefinition findRoute(String routeName) {
        return routes.stream()
                .filter(r -> r.getName().equals(routeName))
                .findFirst()
                .orElse(null);
    }

    /**
     * Convert a route definition into a list of WorkOrderProcess instances.
     *
     * @param routeName the route name
     * @return ordered list of processes ready to attach to a work order
     * @throws IllegalArgumentException if route not found
     */
    public List<WorkOrderProcess> buildProcesses(String routeName) {
        RouteDefinition route = findRoute(routeName);
        if (route == null) {
            throw new IllegalArgumentException("Process route not found: " + routeName);
        }

        List<WorkOrderProcess> processes = new ArrayList<>();
        for (int i = 0; i < route.getProcesses().size(); i++) {
            ProcessDefinition def = route.getProcesses().get(i);
            WorkOrderProcess process = new WorkOrderProcess();
            process.setProcessDefinitionId(null); // YAML-based, no DB reference
            process.setProcessName(def.getName());
            process.setSortOrder(i + 1);
            process.setPlannedHours(def.getPlannedHours());
            process.setNeedInspection(def.getNeedInspection() != null ? def.getNeedInspection() : false);
            process.setUnitCost(def.getUnitCost());
            process.setAllowableLossRate(def.getAllowableLossRate());
            processes.add(process);
        }
        return processes;
    }

    /**
     * A named process route containing an ordered list of process definitions.
     */
    public static class RouteDefinition {
        private String name;
        private String description;
        private List<ProcessDefinition> processes = new ArrayList<>();

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }

        public List<ProcessDefinition> getProcesses() { return processes; }
        public void setProcesses(List<ProcessDefinition> processes) { this.processes = processes; }
    }

    /**
     * A single process step within a route.
     */
    public static class ProcessDefinition {
        private String code;
        private String name;
        private Integer sortOrder;
        private BigDecimal plannedHours;
        private Boolean needInspection;
        private BigDecimal unitCost;
        private BigDecimal allowableLossRate;

        public String getCode() { return code; }
        public void setCode(String code) { this.code = code; }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public Integer getSortOrder() { return sortOrder; }
        public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }

        public BigDecimal getPlannedHours() { return plannedHours; }
        public void setPlannedHours(BigDecimal plannedHours) { this.plannedHours = plannedHours; }

        public Boolean getNeedInspection() { return needInspection; }
        public void setNeedInspection(Boolean needInspection) { this.needInspection = needInspection; }

        public BigDecimal getUnitCost() { return unitCost; }
        public void setUnitCost(BigDecimal unitCost) { this.unitCost = unitCost; }

        public BigDecimal getAllowableLossRate() { return allowableLossRate; }
        public void setAllowableLossRate(BigDecimal allowableLossRate) { this.allowableLossRate = allowableLossRate; }
    }
}
