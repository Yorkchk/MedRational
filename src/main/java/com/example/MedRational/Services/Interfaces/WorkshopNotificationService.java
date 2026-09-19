package com.example.MedRational.Services.Interfaces;

import com.example.MedRational.Entities.WorkshopProject;

public interface WorkshopNotificationService {
    void notifyOpeningSoon(WorkshopProject project);
    void notifyRegistrationOpened(WorkshopProject project);
    void notifyClosingSoon(WorkshopProject project);
    void notifyRegistrationClosed(WorkshopProject project);
}