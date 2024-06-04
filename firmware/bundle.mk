# Use SHORT_BOARD_NAME for BUNDLE_NAME if it's empty.
# BUNDLE_NAME is usually more specific - it points to a variant of a board.
ifeq (,$(BUNDLE_NAME))
  BUNDLE_NAME = $(SHORT_BOARD_NAME)
endif

# If we're running on Windows, we need to call the .exe of hex2dfu
ifneq (,$(findstring NT,$(UNAME_S)))
	H2D = ../misc/encedo_hex2dfu/hex2dfu.exe
else
	H2D = ../misc/encedo_hex2dfu/hex2dfu.bin
endif

# DFU and DBIN are uploaded as artifacts, so it's easier to have them in the deliver/ directory
#  than to try uploading them from the rusefi.snapshot.<BUNDLE_NAME> directory
DFU = $(DELIVER)/$(PROJECT).dfu
DBIN = $(DELIVER)/$(PROJECT).bin
FIRMWARE_BIN_OUT = $(FOLDER)/$(PROJECT).bin

# If provided with a git reference and the LTS flag, use a folder name including the ref
# This weird if statement structure is because Make doesn't have &&
ifeq ($(AUTOMATION_LTS),true)
ifneq (,$(AUTOMATION_REF))
  FOLDER = rusefi.$(AUTOMATION_REF).$(BUNDLE_NAME)
else
  FOLDER = rusefi.lts_unknown.$(BUNDLE_NAME)
endif
else
  FOLDER = rusefi.snapshot.$(BUNDLE_NAME)
endif

DELIVER = deliver
ARTIFACTS = ../artifacts

ifneq (,$(BRANCH_NAME))
	BUNDLE_FULL_NAME_BRANCH_TOKEN = _$(BRANCH_NAME)
endif

BUNDLE_FULL_NAME = rusefi_bundle$(BUNDLE_FULL_NAME_BRANCH_TOKEN)_$(BUNDLE_NAME)

CONSOLE_FOLDER = $(FOLDER)/console
DRIVERS_FOLDER = $(FOLDER)/drivers

UPDATE_FOLDER_SOURCES = \
  $(RUSEFI_CONSOLE_SETTINGS) \
  $(INI_FILE) \
  ../misc/console_launcher/readme.html \
  ../misc/console_launcher/rusefi_updater.exe

FOLDER_SOURCES = \
  ../java_console/bin

# Custom board builds don't include the simulator
ifneq ($(BUNDLE_SIMULATOR),false)
  SIMULATOR_OUT = ../simulator/build/rusefi_simulator.exe
endif

UPDATE_CONSOLE_FOLDER_SOURCES = \
  $(CONSOLE_OUT) \
  $(AUTOUPDATE_OUT)

# todo: remove BootCommander.exe once https://github.com/rusefi/rusefi/issues/6358 is done

CONSOLE_FOLDER_SOURCES = \
  ../misc/console_launcher/rusefi_autoupdate.exe \
  ../misc/console_launcher/rusefi_console.exe \
  ../misc/install/openocd \
  ../misc/install/STM32_Programmer_CLI \
  $(wildcard ../java_console/*.dll) \
  ../firmware/ext/openblt/Host/libopenblt.dll \
  ../firmware/ext/openblt/Host/BootCommander.exe \
  ../firmware/ext/openblt/Host/libopenblt.so \
  ../firmware/ext/openblt/Host/libopenblt.dylib \
  ../firmware/ext/openblt/Host/openblt_jni.dll \
  ../firmware/ext/openblt/Host/libopenblt_jni.so \
  ../firmware/ext/openblt/Host/libopenblt_jni.dylib \
  $(SIMULATOR_OUT)

BOOTLOADER_BIN = bootloader/blbuild/openblt_$(PROJECT_BOARD).bin
BOOTLOADER_HEX = bootloader/blbuild/openblt_$(PROJECT_BOARD).hex

# We need to put different things in the bundle depending on some meta-info flags
ifeq ($(USE_OPENBLT),yes)
  BOOTLOADER_HEX_OUT = $(BOOTLOADER_HEX)
  BOOTLOADER_BIN_OUT = $(FOLDER)/openblt.bin
  SREC_TARGET = $(FOLDER)/rusefi_update.srec
else
  FIRMWARE_OUTPUTS = $(FOLDER)/$(PROJECT).hex
  BINSRC = $(BUILDDIR)/$(PROJECT).bin
ifeq ($(INCLUDE_ELF),yes)
  FIRMWARE_OUTPUTS += $(FOLDER)/$(PROJECT).elf $(FOLDER)/$(PROJECT).map $(FOLDER)/$(PROJECT).list
endif
endif

ST_DRIVERS = $(DRIVERS_FOLDER)/silent_st_drivers2.exe

# We're kinda doing things backwards from the normal Make way.
# We're listing some files we want to get copied from the bundle,
# and these commands turn them into their destination path within the bundle.
UPDATE_FOLDER_TARGETS = $(addprefix $(FOLDER)/,$(notdir $(UPDATE_FOLDER_SOURCES)))
UPDATE_CONSOLE_FOLDER_TARGETS = $(addprefix $(CONSOLE_FOLDER)/,$(notdir $(UPDATE_CONSOLE_FOLDER_SOURCES)))

MOST_COMMON_BUNDLE_FILES = \
  $(UPDATE_FOLDER_TARGETS) \
  $(UPDATE_CONSOLE_FOLDER_TARGETS)

UPDATE_BUNDLE_FILES = \
  $(FIRMWARE_OUTPUTS) \
  $(BOOTLOADER_BIN_OUT) \
  $(FIRMWARE_BIN_OUT) \
  $(SREC_TARGET) \
  $(MOST_COMMON_BUNDLE_FILES)

FOLDER_TARGETS = $(addprefix $(FOLDER)/,$(notdir $(FOLDER_SOURCES)))
CONSOLE_FOLDER_TARGETS = $(addprefix $(CONSOLE_FOLDER)/,$(notdir $(CONSOLE_FOLDER_SOURCES)))

FULL_BUNDLE_CONTENT = \
  $(ST_DRIVERS) \
  $(FOLDER_TARGETS) \
  $(CONSOLE_FOLDER_TARGETS)

BUNDLE_FILES = \
  $(UPDATE_BUNDLE_FILES) \
  $(FULL_BUNDLE_CONTENT)

$(SIMULATOR_OUT): $(CONFIG_FILES) .FORCE
	$(MAKE) -C ../simulator -r OS="Windows_NT" SUBMAKE=yes

# make Windows simulator a prerequisite so that we don't try compiling them concurrently
../simulator/build/rusefi_simulator: $(CONFIG_FILES) .FORCE | $(SIMULATOR_OUT)
	$(MAKE) -C ../simulator -r OS="Linux" SUBMAKE=yes

$(BOOTLOADER_HEX) $(BOOTLOADER_BIN): .bootloader-sentinel ;

# We pass SUBMAKE=yes to the bootloader Make instance so it knows not to try to build configs,
#  as that would result in two simultaneous config generations, which causes issues.
.bootloader-sentinel: $(CONFIG_FILES) .FORCE
	BOARD_DIR=../$(BOARD_DIR) BOARD_META_PATH=../$(BOARD_META_PATH) TGT_SENTINEL=../$(TGT_SENTINEL) $(MAKE) -C bootloader -r SUBMAKE=yes
	@touch $@

$(BUILDDIR)/$(PROJECT).map: $(BUILDDIR)/$(PROJECT).elf

$(SREC_TARGET): $(BUILDDIR)/rusefi.srec
	ln -rfs $< $@

$(FIRMWARE_OUTPUTS): $(FOLDER)/%: $(BUILDDIR)/% | $(FOLDER)
	ln -rfs $< $@

$(BOOTLOADER_BIN_OUT): $(FOLDER)/openblt%: bootloader/blbuild/openblt_$(PROJECT_BOARD)% | $(FOLDER)
	ln -rfs $< $@

$(FIRMWARE_BIN_OUT) $(FOLDER)/$(PROJECT).dfu: $(FOLDER)/%: $(DELIVER)/% | $(FOLDER)
	ln -rfs $< $@

# The DFU is currenly not included in the bundle, so these prerequisites are listed as order-only to avoid building it.
# If you want it, you can build it with `make rusefi.snapshot.$BUNDLE_NAME/rusefi.dfu`
$(DFU) $(DBIN): .h2d-sentinel ;

.h2d-sentinel: $(BUILDDIR)/$(PROJECT).hex $(BOOTLOADER_HEX_OUT) $(BINSRC) | $(DELIVER)
ifeq ($(USE_OPENBLT),yes)
	$(H2D) -i $(BOOTLOADER_HEX) -i $(BUILDDIR)/$(PROJECT).hex -C 0x1C -o $(DFU) -b $(DBIN)
else
	$(H2D) -i $(BUILDDIR)/$(PROJECT).hex -C 0x1C -o $(DFU)
	cp $(BUILDDIR)/$(PROJECT).bin $(DBIN)
endif
	@touch $@

OBFUSCATED_SREC = $(FOLDER)/rusefi-obfuscated.srec

OBFUSCATED_OUT = \
  $(FOLDER)/rusefi-obfuscated.bin \
  $(OBFUSCATED_SREC)

$(OBFUSCATED_OUT): .obfuscated-sentinel

.obfuscated-sentinel: $(BUILDDIR)/$(PROJECT).bin
	[ -z "$(POST_BUILD_SCRIPT)" ] || bash $(POST_BUILD_SCRIPT) $(BUILDDIR)/$(PROJECT).bin $(OBFUSCATED_OUT)
	@touch $@

$(ST_DRIVERS): | $(DRIVERS_FOLDER)
	wget https://rusefi.com/build_server/st_files/silent_st_drivers2.exe -P $(dir $@)

$(DELIVER) $(ARTIFACTS) $(FOLDER) $(CONSOLE_FOLDER) $(DRIVERS_FOLDER):
	mkdir -p $@

$(ARTIFACTS)/$(BUNDLE_FULL_NAME).zip: $(BUNDLE_FILES) | $(ARTIFACTS)
	zip -r $@ $(BUNDLE_FILES)

$(ARTIFACTS)/$(BUNDLE_FULL_NAME)_obfuscated_public.zip:  $(OBFUSCATED_OUT) $(BUNDLE_FILES) | $(ARTIFACTS)
	zip -r $@ $(FULL_BUNDLE_CONTENT) $(MOST_COMMON_BUNDLE_FILES) $(OBFUSCATED_SREC)

# The autopdate zip doesn't have a folder with the bundle contents
$(ARTIFACTS)/$(BUNDLE_FULL_NAME)_autoupdate.zip: $(UPDATE_BUNDLE_FILES) | $(ARTIFACTS)
	cd $(FOLDER) &&	zip -r ../$@ $(subst $(FOLDER)/,,$(UPDATE_BUNDLE_FILES))

$(ARTIFACTS)/$(BUNDLE_FULL_NAME)_obfuscated_public_autoupdate.zip:  $(OBFUSCATED_OUT) $(BUNDLE_FILES) | $(ARTIFACTS)
	cd $(FOLDER) &&	zip -r ../$@ $(subst $(FOLDER)/,,$(MOST_COMMON_BUNDLE_FILES)) $(subst $(FOLDER)/,,$(OBFUSCATED_SREC))

.PHONY: bundle bundles autoupdate obfuscated bin hex dfu map elf list srec bootloader

bundle: $(ARTIFACTS)/$(BUNDLE_FULL_NAME).zip
autoupdate: $(ARTIFACTS)/$(BUNDLE_FULL_NAME)_autoupdate.zip
obfuscated: $(ARTIFACTS)/$(BUNDLE_FULL_NAME)_obfuscated_public.zip $(ARTIFACTS)/$(BUNDLE_FULL_NAME)_obfuscated_public_autoupdate.zip
bundles: bundle autoupdate

bootloader: $(BOOTLOADER_BIN)

bin: $(DBIN)
hex: $(BUILDDIR)/$(PROJECT).hex
dfu: $(DFU)
srec: $(BUILDDIR)/rusefi.srec
elf: $(BUILDDIR)/$(PROJECT).elf
map: $(BUILDDIR)/$(PROJECT).map
list: $(BUILDDIR)/$(PROJECT).list

CLEAN_BUNDLE_HOOK:
	@echo Cleaning Bundle
	$(MAKE) -C ../simulator clean
	BOARD_DIR=../$(BOARD_DIR) BOARD_META_PATH=../$(BOARD_META_PATH) $(MAKE) -C bootloader clean
	rm -rf $(FOLDER)
	rm -rf $(DELIVER)
	rm -f .*-sentinel
	@echo Done Cleaning Bundle

# We need to use secondary expansion on these rules to find the target file in the source file list.
PERCENT = %

.SECONDEXPANSION:
$(FOLDER_TARGETS) $(UPDATE_FOLDER_TARGETS): $(FOLDER)/%: $$(filter $$(PERCENT)$$*,$(FOLDER_SOURCES) $(UPDATE_FOLDER_SOURCES)) | $(FOLDER)
	ln -rfs $< $@

$(CONSOLE_FOLDER_TARGETS) $(UPDATE_CONSOLE_FOLDER_TARGETS): $(CONSOLE_FOLDER)/%: $$(filter $$(PERCENT)$$*,$(CONSOLE_FOLDER_SOURCES) $(UPDATE_CONSOLE_FOLDER_SOURCES)) | $(CONSOLE_FOLDER)
	ln -rfs $< $@
