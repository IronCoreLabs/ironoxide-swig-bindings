mod swig_foreign_types_map {}

foreign_typemap!(
    ($p:r_type) OffsetDateTime => jlong {
        $out = $p.unix_timestamp() * 1000 + $p.millisecond() as i64;
    };
    ($p:f_type, option = "NoNullAnnotations", unique_prefix = "/*offsetdatetime*/") => "/*offsetdatetime*/java.util.Date" "$out = new java.util.Date($p);";
    ($p:f_type, option = "NullAnnotations", unique_prefix = "/*offsetdatetime*/") => "/*offsetdatetime*/@NonNull java.util.Date" "$out = new java.util.Date($p);";
);

foreign_typemap!(
    ($p:r_type) Option<OffsetDateTime> => internal_aliases::JOptionalLong {
        let tmp: Option<i64> = $p.map(|x| $x.unix_timestamp() * 1000 + $x.millisecond() as i64);
        $out = to_java_util_optional_long(env, tmp);
    };
    ($p:f_type, unique_prefix = "/*offsetdatetime*/") => "/*offsetdatetime*/java.util.Optional<java.util.Date>"
        r#"
        $out;
        if ($p.isPresent()) {
            $out = java.util.Optional.of(new java.util.Date($p.getAsLong()));
        } else {
            $out = java.util.Optional.empty();
        }
"#;
);


// this type and typemap is required so that java can accept `&[T]` as a function input.
// it uses the `jobject_array_to_vec_of_objects` function, which clones the inside type for you.
#[repr(transparent)]
pub struct JForeignObjectsArrayForSlice<T: SwigForeignClass> {
    pub(crate) inner: jobjectArray,
    pub(crate) _marker: ::std::marker::PhantomData<T>,
}
foreign_typemap!(
    ($p:r_type) <T: SwigForeignClass + Clone> &[T] <= JForeignObjectsArrayForSlice<T> {
        let arr = crate::internal_aliases::JForeignObjectsArray{ inner: $p.inner, _marker: $p._marker};
        $out = &jobject_array_to_vec_of_objects(env, arr);
    };
    ($p:f_type, option = "NoNullAnnotations", unique_prefix = "/*slice*/") <= "/*slice*/swig_f_type!(T) []";
    ($p:f_type, option = "NullAnnotations", unique_prefix = "/*slice*/") <= "/*slice*/@NonNull swig_f_type!(T, NoNullAnnotations) []";
);