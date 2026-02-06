#![allow(unexpected_cfgs)]

use std::{
    env,
    path::{Path, PathBuf},
    time::Instant,
};

use flapigen::LanguageConfig;

cfg_if::cfg_if! {
if #[cfg(feature = "cpp")] {
    use flapigen::CppConfig;
}else{
    use flapigen::JavaConfig;
}}

fn main() {
    env_logger::init();
    let out_dir = env::var("OUT_DIR").unwrap();
    let out_path = Path::new(&out_dir);

    #[cfg(feature = "java")]
    {
        let java_home = env::var("JAVA_HOME").expect("JAVA_HOME env variable not set");

        let java_include_dir = Path::new(&java_home).join("include");

        let target = env::var("TARGET").expect("target env var not set");
        let java_sys_include_dir = java_include_dir.join(if target.contains("windows") {
            "win32"
        } else if target.contains("darwin") {
            "darwin"
        } else {
            "linux"
        });

        let include_dirs = [java_include_dir, java_sys_include_dir];
        println!("jni include dirs {:?}", include_dirs);

        let jni_h_path =
            search_file_in_directory(&include_dirs[..], "jni.h").expect("Can not find jni.h");
        println!("cargo:rerun-if-changed={}", jni_h_path.display());

        gen_binding(
            &include_dirs[..],
            &jni_h_path,
            &Path::new(&out_dir).join("jni_c_header.rs"),
        )
        .expect("gen_binding failed");
    }

    let now = Instant::now();
    let icl_expanded_lib_rs = format!("{}icl-expanded-lib.rs.in", out_dir);
    // Just before flapigen expands "lib.rs.in", we do our own expansion
    // This takes in "lib.rs.in" and outputs "icl-expanded-lib.rs.in", which is then fed to flapigen.
    expand_equals_and_hashcode_macro(&icl_expanded_lib_rs);
    flapigen_expand(Path::new(&icl_expanded_lib_rs), out_path);
    let expand_time = now.elapsed();
    println!(
        "rust swig expand time: {}",
        expand_time.as_secs() as f64 + (expand_time.subsec_nanos() as f64) / 1_000_000_000.
    );
    println!("cargo:rerun-if-changed=../common/lib.rs.in");
    println!("cargo:rerun-if-changed=../common/lib.rs");
}

#[cfg(feature = "java")]
fn search_file_in_directory<P: AsRef<Path>>(dirs: &[P], file: &str) -> Result<PathBuf, ()> {
    for dir in dirs {
        let dir = dir.as_ref().to_path_buf();
        let file_path = dir.join(file);
        if file_path.exists() && file_path.is_file() {
            return Ok(file_path);
        }
    }
    Err(())
}

#[cfg(feature = "java")]
fn gen_binding<P: AsRef<Path>>(
    include_dirs: &[P],
    c_file_path: &Path,
    output_rust: &Path,
) -> Result<(), String> {
    let mut bindings: bindgen::Builder = bindgen::builder().header(c_file_path.to_str().unwrap());
    bindings = include_dirs.iter().fold(bindings, |acc, x| {
        acc.clang_arg("-I".to_string() + x.as_ref().to_str().unwrap())
    });

    let generated_bindings = bindings
        .generate()
        .map_err(|_| "Failed to generate bindings".to_string())?;
    generated_bindings
        .write_to_file(output_rust)
        .map_err(|err| err.to_string())?;

    Ok(())
}

fn flapigen_expand(from: &Path, out_dir: &Path) {
    println!("Run flapigen_expand");
    cfg_if::cfg_if! {
        if #[cfg(feature = "cpp")]{
            let name = "ironoxide_cpp";
            let config = CppConfig::new(get_cpp_codegen_output_directory(), "sdk".into());

            let swig_gen = flapigen::Generator::new(LanguageConfig::CppConfig(config))
              .merge_type_map("chrono_support", include_str!("../cpp/cpp_typemaps.rs"));
        } else{
            let name = "ironoxide_jvm";
            let swig_gen = flapigen::Generator::new(LanguageConfig::JavaConfig(JavaConfig::new(
                get_java_codegen_output_directory(out_dir),
                "com.ironcorelabs.sdk".into(),
            )))
            .merge_type_map("chrono_support", include_str!("jni_typemaps.rs"));
        }
    }

    swig_gen
        .rustfmt_bindings(false)
        .remove_not_generated_files_from_output_directory(true) //remove outdated *.java or cpp files
        .expand(name, from, out_dir.join("lib.rs"));

    #[cfg(feature = "android")]
    if std::env::var("CARGO_CFG_TARGET_OS").as_deref() == Ok("android") {
        // Post-process the generated lib.rs to inject rustls-platform-verifier
        // initialization into JNI_OnLoad (see https://github.com/Dushistov/flapigen-rs/issues/440).
        let generated = std::fs::read_to_string(out_dir.join("lib.rs"))
            .expect("Failed to read generated lib.rs");
        // The generated JNI_OnLoad ends with `SWIG_JNI_VERSION` followed by the
        // closing brace of the function. Use a regex to handle any whitespace
        // between them. We match only the return-value occurrence (followed by `}`)
        // and not the GetEnv argument (followed by `,`).
        let re = regex::Regex::new(r"SWIG_JNI_VERSION\s*\}")
            .expect("Failed to compile JNI_OnLoad patch regex");
        let patched = re.replacen(
            &generated,
            1,
            "{ crate::init_rustls_platform_verifier(env); SWIG_JNI_VERSION } }",
        );
        assert_ne!(
            *patched, generated,
            "build.rs: Failed to patch JNI_OnLoad — the SWIG_JNI_VERSION pattern was not found \
             in the generated lib.rs. The flapigen output format may have changed."
        );
        std::fs::write(out_dir.join("lib.rs"), patched.as_ref())
            .expect("Failed to write patched lib.rs");
    }
}

cfg_if::cfg_if! {
    if #[cfg(feature = "cpp")]{
        fn get_cpp_codegen_output_directory() -> PathBuf {
            let path = Path::new("generated").join("sdk");
            if !path.exists() {
                std::fs::create_dir_all(&path).unwrap_or_else(|_| panic!("Couldn't create codegen output directory at {:?}.", path));
            }
            println!("Output dir: {:?}", &path);
            path.to_path_buf()
        }
    }
    else {
        fn get_java_codegen_output_directory(out_dir: &Path) -> PathBuf {
            let path = out_dir
                .join("java")
                .join("com")
                .join("ironcorelabs")
                .join("sdk");
            if !path.exists() {
                std::fs::create_dir_all(&path).unwrap_or_else(|_| panic!("Couldn't create codegen output directory at {:?}.", path));
            }
            path
        }
    }
}

fn expand_equals_and_hashcode_macro(out: &str) {
    cfg_if::cfg_if! {
        if #[cfg(feature="cpp")] {
            let equals_and_hashcode = r##"
                private fn eq(&self, o: &$1) -> bool; alias rustEq;
                foreign_code r#"
                friend bool operator==(const ${1}Wrapper &lhs, const ${1}Wrapper &rhs) {
                    return lhs.rustEq(rhs);
                }
                
                friend bool operator!=(const ${1}Wrapper &lhs, const ${1}Wrapper &rhs) {
                    return !(lhs == rhs);
                }
            "#;"##;        
        } else {
            let equals_and_hashcode = r##"fn hash(&self) -> i32; alias hashCode;
                private fn eq(&self, o: &$1) -> bool; alias rustEq;
                foreign_code r#"
                public boolean equals(Object obj) {
                    if(obj instanceof $1){
                        $1 other = ($1) obj;
                        return other.rustEq(this);
                    }
                    return false;
            }
            "#;"##;
        }
    }
    let file = std::fs::read_to_string("../common/lib.rs.in")
        .expect("unable to read source file lib.rs.in");
    let re = regex::Regex::new(r"pre_build_generate_equals_and_hashcode (.*);")
        .expect("unable to parse regex expression");
    let replaced = re.replace_all(&file, equals_and_hashcode).to_string();
    std::fs::write(out, replaced).expect("unable to output file");
}
