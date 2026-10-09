package com.example.demo.service;

import com.example.demo.exception.InvalidClientDateException;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

public final class ClientDates {

    private static final long MAX_CLIENT_DATE_DRIFT_DAYS = 1;

    private ClientDates() {
    }

    // "Hoy" es la fecha local que envía el cliente; sin ella se usa la del servidor (UTC).
    // Ninguna zona horaria difiere más de un día de UTC: una diferencia mayor es un dato inválido.
    public static LocalDate resolveToday(LocalDate clientToday) {
        LocalDate serverToday = LocalDate.now();
        if (clientToday == null) {
            return serverToday;
        }
        if (Math.abs(ChronoUnit.DAYS.between(serverToday, clientToday)) > MAX_CLIENT_DATE_DRIFT_DAYS) {
            throw new InvalidClientDateException("La fecha enviada no coincide con la fecha actual");
        }
        return clientToday;
    }
}
