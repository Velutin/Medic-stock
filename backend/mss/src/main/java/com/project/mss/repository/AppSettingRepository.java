package com.project.mss.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.project.mss.model.entity.AppSetting;

public interface AppSettingRepository extends JpaRepository<AppSetting, String> {
}
