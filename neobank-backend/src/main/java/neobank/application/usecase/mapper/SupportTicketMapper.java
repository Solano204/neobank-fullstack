package neobank.application.usecase.mapper;

import neobank.application.dto.response.SupportTicketResponse;
import neobank.domain.entity.SupportTicket;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface SupportTicketMapper {
    SupportTicketResponse toResponse(SupportTicket ticket);
}
