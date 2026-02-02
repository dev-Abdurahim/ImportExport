package org.example.importexportservice.entity;

import jakarta.persistence.*;
import lombok.*;
import org.example.importexportservice.enums.TradeType;

@Entity
@Table(name = "organizations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Organization {

    @Id
    @Column(name = "inn", unique = true, nullable = false)
    private String inn;

    @Column(columnDefinition = "TEXT")
    private String name;

    @Column(columnDefinition = "TEXT")
    private String shortName;

    private String firstName;
    private String lastName;

    private String region;
    private String district;

    @Enumerated(EnumType.STRING)
    private TradeType type;


}
