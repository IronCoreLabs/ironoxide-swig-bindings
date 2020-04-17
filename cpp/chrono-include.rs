mod swig_foreign_types_map {}

foreign_typemap!(
    ($p:r_type) DateTime<Utc> => i64 {
        $out = $p.timestamp_millis();
    };
);
