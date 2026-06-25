package com.distribution.casestudy.mto.service;

import com.distribution.casestudy.mto.model.Quantity;

/**
 * Interface for querying material inventory availability.
 *
 * <p>Decouples the material readiness check from specific inventory implementations.
 * In the original jewelry system, this was tightly coupled to {@code BizGoldInventory}
 * and {@code BizStoneInventory} tables. This interface allows any inventory system
 * to be plugged in.</p>
 *
 * <p>Implementations could query:</p>
 * <ul>
 *   <li>A database table directly</li>
 *   <li>An external inventory microservice via REST</li>
 *   <li>A warehouse management system (WMS)</li>
 *   <li>A simple in-memory store for testing</li>
 * </ul>
 */
public interface InventoryQueryService {

    /**
     * Query available stock for a specific material.
     *
     * @param materialCategory the material category (e.g. "RAW_MATERIAL", "COMPONENT")
     * @param materialName     the material name/SKU
     * @return available quantity in the material's native unit
     */
    Quantity getAvailableStock(String materialCategory, String materialName);
}
