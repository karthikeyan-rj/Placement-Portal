package com.college.placement.messaging.email;

import com.college.placement.placement.DriveRegistrationOpenedEvent;
import com.college.placement.placement.PlacementDrive;
import com.college.placement.placement.PlacementDriveRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Queues the REGISTRATION_OPEN notification fanout after the drive status commit.
 * Any failure here is logged and swallowed on purpose: the drive status is already
 * durable and must not be reversed by an email problem.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class DriveEmailEventListener {

    private final PlacementDriveRepository driveRepository;
    private final DriveEmailNotificationService driveEmailNotificationService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onRegistrationOpened(DriveRegistrationOpenedEvent event) {
        try {
            PlacementDrive drive = driveRepository.findById(event.driveId()).orElse(null);
            if (drive == null) {
                log.warn("[EMAIL] Drive {} no longer exists; skipping registration notification", event.driveId());
                return;
            }
            driveEmailNotificationService.enqueueRegistrationOpen(
                    drive.getId(),
                    drive.getJobRole(),
                    drive.getCompany().getName(),
                    drive.getPackageLpa(),
                    drive.getDriveDate(),
                    drive.getRegistrationDeadline(),
                    drive.getLocation());
        } catch (RuntimeException e) {
            log.error("[EMAIL] Failed to queue REGISTRATION_OPEN notification for drive {}: {}",
                    event.driveId(), String.valueOf(e.getMessage()).replaceAll("[\\r\\n]", " "));
        }
    }
}
