package cm.ict304.payment.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class DepositRequest {

    @NotBlank(message = "Le numéro de compte est obligatoire")
    private String accountNumber;

    @NotNull(message = "Le montant est obligatoire")
    @DecimalMin(value = "0.01", message = "Le montant minimum de dépôt est 0.01")
    private BigDecimal amount;

    private String description = "Dépôt";
}
