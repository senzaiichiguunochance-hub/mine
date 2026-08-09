#nullable enable
using System;
using System.Diagnostics;
using System.Drawing;
using System.Drawing.Imaging;
using System.IO;
using System.Runtime.InteropServices;
using System.Text.Json;
using System.Windows.Forms;
using Microsoft.Win32;

namespace ClipOutputImg
{
    public partial class MainForm : Form
    {
        #region 内部フィールド・設定クラス

        /// <summary>
        /// アプリケーション設定クラス
        /// </summary>
        public class AppConfig
        {
            public string SaveFolderPath { get; set; } = "";
            public bool IsNotifyEnabled { get; set; } = true;
            public bool IsStartupEnabled { get; set; } = false;
        }

        private AppConfig config = new AppConfig();
        private readonly string configFilePath = Path.Combine(AppDomain.CurrentDomain.BaseDirectory, "config.json");
        private readonly string startupRegistryKey = @"SOFTWARE\Microsoft\Windows\CurrentVersion\Run";
        private readonly string appName = "ClipOutputImg";

        private bool isExitRequested = false;
        private DateTime lastSavedTime = DateTime.MinValue;

        #endregion

        #region 初期化・フォーム ライフサイクル

        public MainForm()
        {
            InitializeComponent();
            SetupCustomEvents();
        }

        /// <summary>
        /// 各種コントロール・フォームのイベントバインド
        /// </summary>
        private void SetupCustomEvents()
        {
            if (notifyIcon1.Icon == null)
            {
                notifyIcon1.Icon = SystemIcons.Application;
            }
            notifyIcon1.ContextMenuStrip = contextMenuStrip1;

            // フォーム制御イベント
            this.FormClosing += Form1_FormClosing;
            this.Resize += Form1_Resize;

            // ボタンクリックイベント
            btnBrowse.Click += BtnBrowse_Click;
            btnOpenExplorer.Click += BtnOpenExplorer_Click;
            btnSaveConfig.Click += BtnSaveConfig_Click;
            BtnHelp.Click += BtnHelp_Click;

            // コンテキストメニューイベント
            menuOpenSetting.Click += MenuOpenSetting_Click;
            menuOpenFolder.Click += MenuOpenFolder_Click;
            menuExit.Click += MenuExit_Click;

            // トレイアイコンイベント
            notifyIcon1.DoubleClick += NotifyIcon1_DoubleClick;
        }

        private void Form1_Load(object? sender, EventArgs e)
        {
            LoadConfig();

            // 起動時はシステムトレイへ収納
            this.BeginInvoke(new Action(() =>
            {
                HideToTray();
            }));
        }

        /// <summary>
        /// ウィンドウハンドル生成時の処理（クリップボード監視の確実な登録）
        /// </summary>
        protected override void OnHandleCreated(EventArgs e)
        {
            base.OnHandleCreated(e);
            NativeMethods.AddClipboardFormatListener(this.Handle);
        }

        private void Form1_FormClosing(object? sender, FormClosingEventArgs e)
        {
            // ユーザーによる[×]ボタンクリック時は終了せずトレイへ隠す
            if (!isExitRequested && e.CloseReason == CloseReason.UserClosing)
            {
                e.Cancel = true;
                HideToTray();
            }
            else
            {
                // アプリ終了時にクリップボード監視を解除
                NativeMethods.RemoveClipboardFormatListener(this.Handle);
                notifyIcon1.Visible = false;
            }
        }

        private void Form1_Resize(object? sender, EventArgs e)
        {
            if (this.WindowState == FormWindowState.Minimized)
            {
                HideToTray();
            }
        }

        #endregion

        #region 設定の読み込み・保存・レジストリ操作

        private void LoadConfig()
        {
            try
            {
                if (File.Exists(configFilePath))
                {
                    string json = File.ReadAllText(configFilePath);
                    config = JsonSerializer.Deserialize<AppConfig>(json) ?? new AppConfig();
                }
            }
            catch (Exception ex)
            {
                Debug.WriteLine($"設定読み込み失敗: {ex.Message}");
            }

            if (string.IsNullOrWhiteSpace(config.SaveFolderPath))
            {
                config.SaveFolderPath = Environment.GetFolderPath(Environment.SpecialFolder.MyPictures);
            }

            txtSavePath.Text = config.SaveFolderPath;
            chkNotify.Checked = config.IsNotifyEnabled;
            chkStartup.Checked = config.IsStartupEnabled;
        }

        private void SaveConfig()
        {
            try
            {
                config.SaveFolderPath = txtSavePath.Text;
                config.IsNotifyEnabled = chkNotify.Checked;
                config.IsStartupEnabled = chkStartup.Checked;

                string json = JsonSerializer.Serialize(config, new JsonSerializerOptions { WriteIndented = true });
                File.WriteAllText(configFilePath, json);

                SetStartup(config.IsStartupEnabled);

                MessageBox.Show("設定を保存しました。", "完了", MessageBoxButtons.OK, MessageBoxIcon.Information);
                HideToTray();
            }
            catch (Exception ex)
            {
                MessageBox.Show($"設定の保存に失敗しました: {ex.Message}", "エラー", MessageBoxButtons.OK, MessageBoxIcon.Error);
            }
        }

        private void SetStartup(bool enable)
        {
            try
            {
                using RegistryKey? key = Registry.CurrentUser.OpenSubKey(startupRegistryKey, true);
                if (key != null)
                {
                    if (enable)
                    {
                        key.SetValue(appName, Application.ExecutablePath);
                    }
                    else if (key.GetValue(appName) != null)
                    {
                        key.DeleteValue(appName);
                    }
                }
            }
            catch (Exception ex)
            {
                Debug.WriteLine($"スタートアップ設定エラー: {ex.Message}");
            }
        }

        #endregion

        #region UIコントロール イベントハンドラー

        private void BtnBrowse_Click(object? sender, EventArgs e)
        {
            using var dlg = new FolderBrowserDialog();
            dlg.SelectedPath = txtSavePath.Text;
            if (dlg.ShowDialog() == DialogResult.OK)
            {
                txtSavePath.Text = dlg.SelectedPath;
            }
        }

        private void BtnOpenExplorer_Click(object? sender, EventArgs e)
        {
            OpenFolderInExplorer(txtSavePath.Text);
        }

        private void BtnSaveConfig_Click(object? sender, EventArgs e)
        {
            SaveConfig();
        }

        private void BtnHelp_Click(object? sender, EventArgs e)
        {
            string helpText =
                "【ClipOutputImg の使い方】\n\n" +
                "以下の操作を行うと、指定したフォルダへ自動でPNG保存されます。\n\n" +
                "1. Webサイトや画像の「右クリック → 画像をコピー」\n" +
                "2. 画像ファイルの「Ctrl + C」（複数選択も対応）\n" +
                "3. 画面キャプチャ（PrintScreenキー / Win + Shift + S など）\n\n" +
                "※アプリは画面右下のシステムトレイに常駐します。";

            MessageBox.Show(helpText, "使い方ガイド", MessageBoxButtons.OK, MessageBoxIcon.Information);
        }

        private void MenuOpenSetting_Click(object? sender, EventArgs e)
        {
            ShowFromTray();
        }

        private void MenuOpenFolder_Click(object? sender, EventArgs e)
        {
            OpenFolderInExplorer(config.SaveFolderPath);
        }

        private void MenuExit_Click(object? sender, EventArgs e)
        {
            isExitRequested = true;
            Application.Exit();
        }

        private void NotifyIcon1_DoubleClick(object? sender, EventArgs e)
        {
            ShowFromTray();
        }

        #endregion

        #region コア機能（クリップボード監視・画像保存）

        protected override void WndProc(ref Message m)
        {
            const int WM_CLIPBOARDUPDATE = 0x031D;
            if (m.Msg == WM_CLIPBOARDUPDATE)
            {
                SaveImageFromClipboard();
            }
            base.WndProc(ref m);
        }

        /// <summary>
        /// クリップボード内の画像・ファイルを判別して自動保存
        /// </summary>
        private void SaveImageFromClipboard()
        {
            // 連続イベント（1秒以内の重複発火）をブロック
            if ((DateTime.Now - lastSavedTime).TotalMilliseconds < 1000)
            {
                return;
            }

            try
            {
                string folder = config.SaveFolderPath;
                if (!Directory.Exists(folder))
                {
                    Directory.CreateDirectory(folder);
                }

                string fileName = $"img_{DateTime.Now:yyyyMMdd_HHmmss_fff}.png";
                string fullPath = Path.Combine(folder, fileName);

                // パターン1: Webやキャプチャからのビットマップデータ保存
                if (Clipboard.ContainsImage())
                {
                    using Image? img = Clipboard.GetImage();
                    if (img != null)
                    {
                        img.Save(fullPath, ImageFormat.Png);
                        lastSavedTime = DateTime.Now;
                        ShowNotification(fileName);
                        return;
                    }
                }
                // パターン2: エクスプローラー等でのファイル(Ctrl+C)コピーからの保存
                else if (Clipboard.ContainsFileDropList())
                {
                    var fileList = Clipboard.GetFileDropList();
                    bool isSaved = false;

                    foreach (string? filePath in fileList)
                    {
                        if (string.IsNullOrEmpty(filePath) || !File.Exists(filePath)) continue;

                        string ext = Path.GetExtension(filePath).ToLower();
                        if (ext == ".png" || ext == ".jpg" || ext == ".jpeg" || ext == ".bmp" || ext == ".gif" || ext == ".webp")
                        {
                            using Image img = Image.FromFile(filePath);

                            string multiFileName = $"img_{DateTime.Now:yyyyMMdd_HHmmss_fff}_{Guid.NewGuid().ToString().Substring(0, 4)}.png";
                            string multiFullPath = Path.Combine(folder, multiFileName);

                            img.Save(multiFullPath, ImageFormat.Png);
                            isSaved = true;
                        }
                    }

                    if (isSaved)
                    {
                        lastSavedTime = DateTime.Now;
                        ShowNotification("ファイルからのコピー画像を保存しました");
                    }
                }
            }
            catch (Exception ex)
            {
                Debug.WriteLine($"保存スキップ: {ex.Message}");
            }
        }

        #endregion

        #region ヘルパーメソッド

        private void HideToTray()
        {
            this.Hide();
            this.ShowInTaskbar = false;
            notifyIcon1.Visible = true;
        }

        private void ShowFromTray()
        {
            this.Show();
            this.WindowState = FormWindowState.Normal;
            this.ShowInTaskbar = true;
            this.Activate();
        }

        private void OpenFolderInExplorer(string path)
        {
            if (Directory.Exists(path))
            {
                Process.Start("explorer.exe", path);
            }
            else
            {
                MessageBox.Show("指定されたフォルダが存在しません。", "警告", MessageBoxButtons.OK, MessageBoxIcon.Warning);
            }
        }

        private void ShowNotification(string message)
        {
            if (config.IsNotifyEnabled)
            {
                notifyIcon1.ShowBalloonTip(1500, "画像保存完了", message, ToolTipIcon.Info);
            }
        }

        #endregion
    }

    #region Win32 API 宣言

    internal static class NativeMethods
    {
        [DllImport("user32.dll", SetLastError = true)]
        [return: MarshalAs(UnmanagedType.Bool)]
        public static extern bool AddClipboardFormatListener(IntPtr hwnd);

        [DllImport("user32.dll", SetLastError = true)]
        [return: MarshalAs(UnmanagedType.Bool)]
        public static extern bool RemoveClipboardFormatListener(IntPtr hwnd);
    }

    #endregion
}