package org.example.importexportservice.dto.repsonse;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class IndividualResponseDto {

    @JsonProperty("firstname")
    private String firstname;

    @JsonProperty("lastname")
    private String lastname;

    @JsonProperty("registration_address_region_soato")
    private Integer registrationRegionSoato;

    @JsonProperty("cert_given_by")
    private String certGivenBy;
}
