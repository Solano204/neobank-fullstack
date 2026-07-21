package neobank.application.usecase.mapper;

import neobank.application.dto.response.NotificationResponse;
import neobank.domain.entity.Notification;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface NotificationMapper {
    NotificationResponse toResponse(Notification notification);
}
