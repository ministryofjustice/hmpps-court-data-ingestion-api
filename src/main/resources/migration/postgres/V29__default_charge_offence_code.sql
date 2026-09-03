UPDATE court_charge
    SET code = "UNKNOWN"
    WHERE code == null;
