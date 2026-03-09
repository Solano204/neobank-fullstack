package neobank.application.usecase.mapper;

import neobank.application.dto.response.AccountResponse;
import neobank.domain.entity.Account;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface AccountMapper {

    AccountResponse toResponse(Account account);
}