package com.project.mss.dto.imports;

import java.util.List;

/** Summary of a spreadsheet import. errors lists the spreadsheet row and the reason. */
public record ImportResultDTO(int rowsRead, int imported, int ignored, List<String> errors) { }
