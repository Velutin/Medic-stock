package com.project.mss.model.enums;

/**
 * HOSPITAL: regular hospital (surgeries, own storeroom).
 * DISTRIBUTION_CENTER: receives material for a group of hospitals (e.g. SESAB) and only keeps
 * it in the storeroom until it is delivered to one of them; it has no surgeries or stock inside a hospital.
 */
public enum HospitalType {
    HOSPITAL,
    DISTRIBUTION_CENTER
}
