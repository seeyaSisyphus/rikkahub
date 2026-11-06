# material-color-utilities

自定义主题的配色算法, 由 `:material3` 模块作为源码编译。

## 来源

`kotlin/` 取自 [material-color-utilities](https://github.com/material-foundation/material-color-utilities)
的 `kotlin` 目录, commit `6fd88eb3e95ba1d457842e2a2bf847d06b3a018a`, 按 Apache License 2.0 发布,
全文见 [kotlin/LICENSE](kotlin/LICENSE)。代码未做修改, 包名保持上游的 `hct`、`dynamiccolor` 等不变。

> 本 fork 取 `6fd88eb`（含 ColorSpec2025 的黄/青色调色度修正），而非上游内置的 `5b3618b`：
> `CustomTheme` 使用 `SPEC_2025` 生成配色，需要该版本的修正结果。

## 更新

用上游对应 commit 的 `kotlin` 目录整体覆盖 `kotlin/`, 再更新上面的 commit。该目录已从 spotless 排除, 不要格式化。
