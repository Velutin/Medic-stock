package com.project.mss.dto.stock;

import com.project.mss.model.enums.Location;

public record LotBalanceDTO(Long hospitalId, String hospital, Location location, int quantity) { }
