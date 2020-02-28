use std::{
    env,
    path::{Path, PathBuf},
    time::Instant,
};

use rust_swig::{JavaConfig, LanguageConfig};

fn main() {
    env_logger::init();

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
    let out_dir = env::var("OUT_DIR").unwrap();

    gen_binding(
        &include_dirs[..],
        &jni_h_path,
        &Path::new(&out_dir).join("jni_c_header.rs"),
    )
    .expect("gen_binding failed");

    let now = Instant::now();
    let gen_path = Path::new(&out_dir).join("lib.rs");
    let lib_rs_out = out_dir + "lib.rs.out";
    expand_equality_macro(&lib_rs_out);
    rust_swig_expand(Path::new(&lib_rs_out), &gen_path);
    let expand_time = now.elapsed();
    println!(
        "rust swig expand time: {}",
        expand_time.as_secs() as f64 + (expand_time.subsec_nanos() as f64) / 1_000_000_000.
    );
    println!("cargo:rerun-if-changed=src/lib.rs.in");
    println!("cargo:rerun-if-changed=src/lib.rs");
}

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

fn rust_swig_expand(from: &Path, out: &Path) {
    println!("Run rust_swig_expand");
    let swig_gen = rust_swig::Generator::new(LanguageConfig::JavaConfig(JavaConfig::new(
        get_java_codegen_output_directory(),
        "com.ironcorelabs.sdk".into(),
    )))
    .merge_type_map("chrono_support", include_str!("src/chrono-include.rs"))
    .rustfmt_bindings(true)
    .remove_not_generated_files_from_output_directory(true); //remove outdated *.java files
    swig_gen.expand("rust_swig_test_jni", from, out);
}

fn get_java_codegen_output_directory() -> PathBuf {
    let path = Path::new("java")
        .join("com")
        .join("ironcorelabs")
        .join("sdk");
    if !path.exists() {
        std::fs::create_dir_all(&path)
            .expect("Couldn't create codegen output directory at java/com/ironcorelabs/sdk.");
    }
    path.to_path_buf()
}

fn expand_equality_macro(out: &str) {
    let file =
        std::fs::read_to_string("src/lib.rs.in").expect("unable to read source file lib.rs.in");
    let re = regex::Regex::new(r"pre_build_generate_equality (.*);")
        .expect("unable to parse regex expression");
    let replaced = re
        .replace_all(
            &file,
            r##"private fn eq(&self, o: &$1) -> bool; alias rustEq;
            foreign_code r#"
            public boolean equals(Object obj) {
                if(obj instanceof $1){
                    $1 other = ($1) obj;
                    return other.rustEq(this);
                }
            return false;
        }
        "#;"##,
        )
        .to_string();
    std::fs::write(out, replaced).expect("unable to output file");
}
