package com.hydro.plsbl.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;
import org.springframework.data.convert.WritingConverter;
import org.springframework.data.jdbc.core.convert.JdbcCustomConversions;
import org.springframework.data.jdbc.repository.config.AbstractJdbcConfiguration;

import java.math.BigDecimal;
import java.util.Arrays;

/**
 * Konfiguration für Oracle-spezifische JDBC-Konvertierungen
 */
@Configuration
public class OracleJdbcConfig extends AbstractJdbcConfiguration {

    @Override
    @Bean
    public JdbcCustomConversions jdbcCustomConversions() {
        return new JdbcCustomConversions(Arrays.asList(
            new BooleanToNumberConverter(),
            new NumberToBooleanConverter(),
            new BigDecimalToBooleanConverter()
        ));
    }

    /**
     * Konvertiert Boolean zu Number (0/1) für Oracle
     */
    @WritingConverter
    public static class BooleanToNumberConverter implements Converter<Boolean, Number> {
        @Override
        public Number convert(Boolean source) {
            return source != null && source ? 1 : 0;
        }
    }

    /**
     * Konvertiert Number zu Boolean für Oracle
     */
    @ReadingConverter
    public static class NumberToBooleanConverter implements Converter<Number, Boolean> {
        @Override
        public Boolean convert(Number source) {
            return source != null && source.intValue() != 0;
        }
    }

    /**
     * Konvertiert BigDecimal zu Boolean (Oracle gibt oft BigDecimal zurück)
     */
    @ReadingConverter
    public static class BigDecimalToBooleanConverter implements Converter<BigDecimal, Boolean> {
        @Override
        public Boolean convert(BigDecimal source) {
            return source != null && source.intValue() != 0;
        }
    }
}
