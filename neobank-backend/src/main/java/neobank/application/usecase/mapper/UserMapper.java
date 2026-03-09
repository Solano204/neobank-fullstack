package neobank.application.usecase.mapper;


import neobank.application.dto.response.UserProfileResponse;
import neobank.domain.entity.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface UserMapper {

    @Mapping(target = "address.street", source = "street")
    @Mapping(target = "address.city", source = "city")
    @Mapping(target = "address.state", source = "state")
    @Mapping(target = "address.postalCode", source = "postalCode")
    @Mapping(target = "address.country", source = "country")
    UserProfileResponse toProfileResponse(User user);
}