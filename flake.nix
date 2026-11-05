{
  inputs.nixpkgs.url = "github:NixOS/nixpkgs/nixos-25.11";

  outputs = { nixpkgs, ... }:

    let
      systems = [ "aarch64-darwin" "x86_64-darwin" "x86_64-linux" "aarch64-linux" ];
      forEachSystem = nixpkgs.lib.genAttrs systems;

    in
    {
      devShells = forEachSystem (system:

        let
          pkgs = import nixpkgs { inherit system; };

          chosenJdk = pkgs.jdk25_headless;
          chosenGradle = pkgs.gradle_9.override { java = chosenJdk; };

        in
        {
          buildtime = pkgs.mkShell {
            packages = [
              chosenJdk
              chosenGradle
            ];

            JAVA_HOME = "${chosenJdk}";
            GRADLE_HOME = "${chosenGradle}/lib/gradle";

            # Gradle uses an unsafe JDK operation, this flag removes the warning
            JDK_JAVA_OPTIONS = "--enable-native-access=ALL-UNNAMED";

            BUILD_COMMAND = "gradle build";
            UPDATE_IDIOMATIC_LOCK_COMMAND = "gradle dependencies --write-locks";

            IDE_PATH_VARS = "JAVA_HOME GRADLE_HOME";

            RELEASE_TYPE = "container";
          };
        }

      );

      packages = forEachSystem (system:

        let
          pkgs = import nixpkgs { inherit system; };
          chosenJdk = pkgs.jdk25_headless;

        in
        {
          runtime = pkgs.writeShellApplication {
            name = "ignite";
            runtimeInputs = [ chosenJdk ];
            runtimeEnv.JAVA_HOME = "${chosenJdk}";
            text = ''
              idiomatic_artifacts_dir="$1"
              # Guice uses an unsafe JDK operation, this flag removes the warning
              exec java -Dguice_custom_class_loading=CHILD -XX:MaxRAMPercentage=75.0 -cp "$idiomatic_artifacts_dir/*" io.materializer.Main
            '';
          };
        }

      );
    };
}
