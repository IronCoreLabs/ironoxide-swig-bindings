mod swig_foreign_types_map {}

foreign_typemap!(
    ($p:r_type) OffsetDateTime => i64 {
        $out = $p.unix_timestamp() * 1000 + $p.millisecond() as i64;
    };
);
