package neobank.application.usecase.mapper;


import neobank.application.dto.response.ContactResponse;
import neobank.domain.entity.Contact;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ContactMapper {

    @Mapping(target = "name", source = "contactName")
    ContactResponse toResponse(Contact contact);
}