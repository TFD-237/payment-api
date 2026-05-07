package cm.ict304.payment.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

// ─── Création de compte ───────────────────────────────────────────────────────
@Getter @Setter @NoArgsConstructor
class CreateAccountRequest {
    @NotBlank(message = "Le nom du titulaire est obligatoire")
    @Size(min = 2, max = 100)
    private String ownerName;

    @DecimalMin(value = "0.0", inclusive = true, message = "Le solde initial ne peut pas être négatif")
    private BigDecimal initialBalance = BigDecimal.ZERO;
}
