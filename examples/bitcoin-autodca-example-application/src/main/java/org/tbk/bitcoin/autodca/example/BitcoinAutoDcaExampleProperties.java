package org.tbk.bitcoin.autodca.example;

import com.google.common.base.Strings;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.knowm.xchange.currency.Currency;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.ConstructorBinding;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;
import java.util.Optional;

@ConfigurationProperties(
        prefix = "org.tbk.bitcoin.autodca",
        ignoreUnknownFields = false
)
@Getter
@AllArgsConstructor(onConstructor = @__(@ConstructorBinding))
public class BitcoinAutoDcaExampleProperties implements Validator {
    private static final BigDecimal MAXIMUM_MAX_FEE_VALUE = new BigDecimal("5");

    /**
     * the governmental shitcoin you are selling.
     * See {@link Currency#getAvailableCurrencyCodes()}
     *
     * @param fiatCurrency the governmental shitcoin you are selling.
     * @return the governmental shitcoin you are selling.
     */
    private String fiatCurrency;

    /**
     * fiat amount you trade for the future of money.
     *
     * @param fiatAmount fiat amount you trade for the future of money.
     * @return fiat amount you trade for the future of money.
     */
    private BigDecimal fiatAmount;

    /**
     * maximum fee in % that you are willing to pay.
     *
     * @param maxRelativeFee maximum fee in % that you are willing to pay.
     * @return maximum fee in % that you are willing to pay.
     */
    private BigDecimal maxRelativeFee;

    /**
     * description of withdrawal address.
     *
     * @param withdrawAddress description of withdrawal address.
     * @return description of withdrawal address.
     */
    private String withdrawAddress;

    private Boolean dry;

    public boolean getDry() {
        return Objects.requireNonNullElse(dry, false);
    }

    @Override
    public boolean supports(Class<?> clazz) {
        return clazz == BitcoinAutoDcaExampleProperties.class;
    }

    @Override
    public void validate(Object target, Errors errors) {
        BitcoinAutoDcaExampleProperties properties = (BitcoinAutoDcaExampleProperties) target;

        String fiatCurrency = properties.getFiatCurrency();
        if (Strings.isNullOrEmpty(fiatCurrency)) {
            String errorMessage = "'fiatCurrency' entry must not be empty";
            errors.rejectValue("fiatCurrency", "fiatCurrency.invalid", errorMessage);
        } else {
            boolean fiatCurrencyIsAvailable = Currency.getAvailableCurrencyCodes()
                    .contains(properties.getFiatCurrency());

            if (!fiatCurrencyIsAvailable) {
                String errorMessage = "'fiatCurrency' is not available - given: " + fiatCurrency;
                errors.rejectValue("fiatCurrency", "fiatCurrency.invalid", errorMessage);
            }
        }

        BigDecimal fiatAmount = properties.getFiatAmount();
        if (fiatAmount == null) {
            String errorMessage = "'fiatAmount' entry must not be empty";
            errors.rejectValue("fiatAmount", "fiatAmount.invalid", errorMessage);
        } else if (fiatAmount.compareTo(BigDecimal.ZERO) <= 0) {
            String errorMessage = "'fiatAmount' entry must be a positive number - e.g 21.1";
            errors.rejectValue("fiatAmount", "fiatAmount.invalid", errorMessage);
        }

        BigDecimal maxRelativeFee = properties.getMaxRelativeFee().orElse(null);
        if (maxRelativeFee == null) {
            String errorMessage = "'maxRelativeFee' entry must not be empty";
            errors.rejectValue("maxRelativeFee", "maxRelativeFee.invalid", errorMessage);
        } else if (maxRelativeFee.compareTo(BigDecimal.ZERO) <= 0) {
            String errorMessage = "'maxRelativeFee' entry must be a positive number - e.g. 0.5 (percent)";
            errors.rejectValue("maxRelativeFee", "maxRelativeFee.invalid", errorMessage);
        } else if (maxRelativeFee.compareTo(MAXIMUM_MAX_FEE_VALUE) > 0) {
            String errorMessage = "'maxRelativeFee' entry must not be greater than 5% - this seems like a user config mistake";
            errors.rejectValue("maxRelativeFee", "maxRelativeFee.invalid", errorMessage);
        }

        String withdrawalAddress = properties.getWithdrawAddress();
        if (Strings.isNullOrEmpty(withdrawalAddress)) {
            String errorMessage = "'withdrawAddress' entry must not be empty";
            errors.rejectValue("withdrawAddress", "withdrawAddress.invalid", errorMessage);
        }
    }

    public Optional<BigDecimal> getMaxRelativeFee() {
        return Optional.ofNullable(maxRelativeFee)
                .map(it -> it.setScale(8, RoundingMode.HALF_DOWN))
                .map(it -> it.divide(BigDecimal.valueOf(100L), RoundingMode.HALF_DOWN));
    }
}
