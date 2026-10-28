package com.example.inputmonitor.platform.ime

/**
 * Model representing an individual key on the virtual keyboard.
 */
data class KeyModel(
    val label: String,
    val action: KeyAction,
    val weight: Float = 1.0f,
    val isSpecial: Boolean = false
)

/**
 * A horizontal row of keys.
 */
data class KeyboardRow(
    val keys: List<KeyModel>
)

/**
 * Representation of a full keyboard layout (SRS.md §21, §23, Architecture.md §26).
 */
data class KeyboardLayout(
    val type: KeyboardLayoutType,
    val rows: List<KeyboardRow>
) {
    companion object {

        /**
         * Builds standard English QWERTY, Email, or URI layouts.
         */
        fun createQwertyLayout(
            shiftState: ShiftState = ShiftState.OFF,
            enterLabel: String = "↵",
            type: KeyboardLayoutType = KeyboardLayoutType.QWERTY
        ): KeyboardLayout {
            val isShifted = shiftState.isShifted
            val row1Chars = listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p")
            val row2Chars = listOf("a", "s", "d", "f", "g", "h", "j", "k", "l")
            val row3Chars = listOf("z", "x", "c", "v", "b", "n", "m")

            val row1Keys = row1Chars.map { char ->
                val text = if (isShifted) char.uppercase() else char
                KeyModel(label = text, action = KeyAction.Character(text))
            }

            val row2Keys = row2Chars.map { char ->
                val text = if (isShifted) char.uppercase() else char
                KeyModel(label = text, action = KeyAction.Character(text))
            }

            val shiftLabel = when (shiftState) {
                ShiftState.CAPS_LOCK -> "⇪"
                ShiftState.ON_ONCE -> "⬆"
                ShiftState.OFF -> "⇧"
            }

            val row3Keys = buildList {
                add(KeyModel(label = shiftLabel, action = KeyAction.Shift, weight = 1.4f, isSpecial = true))
                addAll(row3Chars.map { char ->
                    val text = if (isShifted) char.uppercase() else char
                    KeyModel(label = text, action = KeyAction.Character(text))
                })
                add(KeyModel(label = "⌫", action = KeyAction.Backspace, weight = 1.4f, isSpecial = true))
            }

            val row4Keys = when (type) {
                KeyboardLayoutType.EMAIL -> listOf(
                    KeyModel(label = "?123", action = KeyAction.SwitchLayout(KeyboardLayoutType.SYMBOLS), weight = 1.3f, isSpecial = true),
                    KeyModel(label = "@", action = KeyAction.Character("@"), weight = 1.2f, isSpecial = true),
                    KeyModel(label = "SPACE", action = KeyAction.Space, weight = 3.5f),
                    KeyModel(label = ".com", action = KeyAction.Character(".com"), weight = 1.4f, isSpecial = true),
                    KeyModel(label = enterLabel, action = KeyAction.Enter, weight = 1.6f, isSpecial = true)
                )
                KeyboardLayoutType.URI -> listOf(
                    KeyModel(label = "?123", action = KeyAction.SwitchLayout(KeyboardLayoutType.SYMBOLS), weight = 1.3f, isSpecial = true),
                    KeyModel(label = "/", action = KeyAction.Character("/"), weight = 1.1f, isSpecial = true),
                    KeyModel(label = "SPACE", action = KeyAction.Space, weight = 3.2f),
                    KeyModel(label = ".", action = KeyAction.Character("."), weight = 1.0f),
                    KeyModel(label = ".com", action = KeyAction.Character(".com"), weight = 1.4f, isSpecial = true),
                    KeyModel(label = enterLabel, action = KeyAction.Enter, weight = 1.6f, isSpecial = true)
                )
                else -> listOf(
                    KeyModel(label = "?123", action = KeyAction.SwitchLayout(KeyboardLayoutType.SYMBOLS), weight = 1.4f, isSpecial = true),
                    KeyModel(label = ",", action = KeyAction.Character(","), weight = 1.0f),
                    KeyModel(label = "🌐", action = KeyAction.SwitchIme, weight = 1.0f, isSpecial = true),
                    KeyModel(label = "SPACE", action = KeyAction.Space, weight = 4.0f),
                    KeyModel(label = ".", action = KeyAction.Character("."), weight = 1.0f),
                    KeyModel(label = enterLabel, action = KeyAction.Enter, weight = 1.6f, isSpecial = true)
                )
            }

            return KeyboardLayout(
                type = type,
                rows = listOf(
                    KeyboardRow(row1Keys),
                    KeyboardRow(row2Keys),
                    KeyboardRow(row3Keys),
                    KeyboardRow(row4Keys)
                )
            )
        }

        /**
         * Builds primary Symbols layout with numbers 1-0 and common punctuation.
         */
        fun createSymbolsLayout(enterLabel: String = "↵"): KeyboardLayout {
            val row1Keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0").map {
                KeyModel(label = it, action = KeyAction.Character(it))
            }

            val row2Keys = listOf("@", "#", "$", "%", "&", "-", "+", "(", ")", "/").map {
                KeyModel(label = it, action = KeyAction.Character(it))
            }

            val row3Keys = buildList {
                add(KeyModel(label = "=<\$", action = KeyAction.SwitchLayout(KeyboardLayoutType.SYMBOLS_MORE), weight = 1.4f, isSpecial = true))
                addAll(listOf("*", "\"", "'", ":", ";", "!", "?").map {
                    KeyModel(label = it, action = KeyAction.Character(it))
                })
                add(KeyModel(label = "⌫", action = KeyAction.Backspace, weight = 1.4f, isSpecial = true))
            }

            val row4Keys = listOf(
                KeyModel(label = "ABC", action = KeyAction.SwitchLayout(KeyboardLayoutType.QWERTY), weight = 1.4f, isSpecial = true),
                KeyModel(label = "🌐", action = KeyAction.SwitchIme, weight = 1.0f, isSpecial = true),
                KeyModel(label = ",", action = KeyAction.Character(","), weight = 1.0f),
                KeyModel(label = "SPACE", action = KeyAction.Space, weight = 4.0f),
                KeyModel(label = ".", action = KeyAction.Character("."), weight = 1.0f),
                KeyModel(label = enterLabel, action = KeyAction.Enter, weight = 1.6f, isSpecial = true)
            )

            return KeyboardLayout(
                type = KeyboardLayoutType.SYMBOLS,
                rows = listOf(
                    KeyboardRow(row1Keys),
                    KeyboardRow(row2Keys),
                    KeyboardRow(row3Keys),
                    KeyboardRow(row4Keys)
                )
            )
        }

        /**
         * Builds extended Symbols layout for math and rare symbols.
         */
        fun createSymbolsMoreLayout(enterLabel: String = "↵"): KeyboardLayout {
            val row1Keys = listOf("~", "`", "|", "^", "\\", "<", ">", "{", "}", "=").map {
                KeyModel(label = it, action = KeyAction.Character(it))
            }

            val row2Keys = listOf("[", "]", "°", "•", "§", "€", "£", "¥", "¢", "_").map {
                KeyModel(label = it, action = KeyAction.Character(it))
            }

            val row3Keys = buildList {
                add(KeyModel(label = "?123", action = KeyAction.SwitchLayout(KeyboardLayoutType.SYMBOLS), weight = 1.4f, isSpecial = true))
                addAll(listOf("«", "»", "¡", "¿", "©", "®", "™").map {
                    KeyModel(label = it, action = KeyAction.Character(it))
                })
                add(KeyModel(label = "⌫", action = KeyAction.Backspace, weight = 1.4f, isSpecial = true))
            }

            val row4Keys = listOf(
                KeyModel(label = "ABC", action = KeyAction.SwitchLayout(KeyboardLayoutType.QWERTY), weight = 1.4f, isSpecial = true),
                KeyModel(label = "🌐", action = KeyAction.SwitchIme, weight = 1.0f, isSpecial = true),
                KeyModel(label = ",", action = KeyAction.Character(","), weight = 1.0f),
                KeyModel(label = "SPACE", action = KeyAction.Space, weight = 4.0f),
                KeyModel(label = ".", action = KeyAction.Character("."), weight = 1.0f),
                KeyModel(label = enterLabel, action = KeyAction.Enter, weight = 1.6f, isSpecial = true)
            )

            return KeyboardLayout(
                type = KeyboardLayoutType.SYMBOLS_MORE,
                rows = listOf(
                    KeyboardRow(row1Keys),
                    KeyboardRow(row2Keys),
                    KeyboardRow(row3Keys),
                    KeyboardRow(row4Keys)
                )
            )
        }

        /**
         * Builds numeric / keypad layout for numeric input fields.
         */
        fun createNumericLayout(enterLabel: String = "↵"): KeyboardLayout {
            val row1Keys = listOf("1", "2", "3").map { KeyModel(label = it, action = KeyAction.Character(it)) }
            val row2Keys = listOf("4", "5", "6").map { KeyModel(label = it, action = KeyAction.Character(it)) }
            val row3Keys = listOf("7", "8", "9").map { KeyModel(label = it, action = KeyAction.Character(it)) }
            val row4Keys = listOf(
                KeyModel(label = "ABC", action = KeyAction.SwitchLayout(KeyboardLayoutType.QWERTY), weight = 1.0f, isSpecial = true),
                KeyModel(label = "0", action = KeyAction.Character("0"), weight = 1.0f),
                KeyModel(label = ".", action = KeyAction.Character("."), weight = 1.0f),
                KeyModel(label = "⌫", action = KeyAction.Backspace, weight = 1.0f, isSpecial = true),
                KeyModel(label = enterLabel, action = KeyAction.Enter, weight = 1.2f, isSpecial = true)
            )

            return KeyboardLayout(
                type = KeyboardLayoutType.NUMERIC,
                rows = listOf(
                    KeyboardRow(row1Keys),
                    KeyboardRow(row2Keys),
                    KeyboardRow(row3Keys),
                    KeyboardRow(row4Keys)
                )
            )
        }

        /**
         * Builds phone dialpad layout for telephone fields.
         */
        fun createPhoneLayout(enterLabel: String = "↵"): KeyboardLayout {
            val row1Keys = listOf("1", "2", "3").map { KeyModel(label = it, action = KeyAction.Character(it)) }
            val row2Keys = listOf("4", "5", "6").map { KeyModel(label = it, action = KeyAction.Character(it)) }
            val row3Keys = listOf("7", "8", "9").map { KeyModel(label = it, action = KeyAction.Character(it)) }
            val row4Keys = listOf(
                KeyModel(label = "+", action = KeyAction.Character("+"), weight = 1.0f, isSpecial = true),
                KeyModel(label = "*", action = KeyAction.Character("*"), weight = 1.0f),
                KeyModel(label = "0", action = KeyAction.Character("0"), weight = 1.0f),
                KeyModel(label = "#", action = KeyAction.Character("#"), weight = 1.0f),
                KeyModel(label = "⌫", action = KeyAction.Backspace, weight = 1.0f, isSpecial = true),
                KeyModel(label = enterLabel, action = KeyAction.Enter, weight = 1.2f, isSpecial = true)
            )

            return KeyboardLayout(
                type = KeyboardLayoutType.PHONE,
                rows = listOf(
                    KeyboardRow(row1Keys),
                    KeyboardRow(row2Keys),
                    KeyboardRow(row3Keys),
                    KeyboardRow(row4Keys)
                )
            )
        }
    }
}
