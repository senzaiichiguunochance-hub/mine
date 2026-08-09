namespace ClipOutputImg
{
    partial class MainForm
    {
        /// <summary>
        ///  Required designer variable.
        /// </summary>
        private System.ComponentModel.IContainer components = null;

        /// <summary>
        ///  Clean up any resources being used.
        /// </summary>
        /// <param name="disposing">true if managed resources should be disposed; otherwise, false.</param>
        protected override void Dispose(bool disposing)
        {
            if (disposing && (components != null))
            {
                components.Dispose();
            }
            base.Dispose(disposing);
        }

        #region Windows Form Designer generated code

        /// <summary>
        ///  Required method for Designer support - do not modify
        ///  the contents of this method with the code editor.
        /// </summary>
        private void InitializeComponent()
        {
            components = new System.ComponentModel.Container();
            lblSavePath = new Label();
            txtSavePath = new TextBox();
            btnBrowse = new Button();
            btnOpenExplorer = new Button();
            chkNotify = new CheckBox();
            chkStartup = new CheckBox();
            btnSaveConfig = new Button();
            notifyIcon1 = new NotifyIcon(components);
            contextMenuStrip1 = new ContextMenuStrip(components);
            menuOpenSetting = new ToolStripMenuItem();
            menuOpenFolder = new ToolStripMenuItem();
            toolStripSeparator1 = new ToolStripSeparator();
            menuExit = new ToolStripMenuItem();
            BtnHelp = new Button();
            contextMenuStrip1.SuspendLayout();
            SuspendLayout();
            // 
            // lblSavePath
            // 
            lblSavePath.AutoSize = true;
            lblSavePath.Location = new Point(12, 44);
            lblSavePath.Name = "lblSavePath";
            lblSavePath.Size = new Size(105, 15);
            lblSavePath.TabIndex = 1;
            lblSavePath.Text = "画像保存先フォルダ:";
            // 
            // txtSavePath
            // 
            txtSavePath.Location = new Point(12, 62);
            txtSavePath.Name = "txtSavePath";
            txtSavePath.Size = new Size(300, 23);
            txtSavePath.TabIndex = 2;
            // 
            // btnBrowse
            // 
            btnBrowse.Location = new Point(12, 91);
            btnBrowse.Name = "btnBrowse";
            btnBrowse.Size = new Size(147, 23);
            btnBrowse.TabIndex = 3;
            btnBrowse.Text = "参照...";
            btnBrowse.UseVisualStyleBackColor = true;
            // 
            // btnOpenExplorer
            // 
            btnOpenExplorer.Location = new Point(165, 91);
            btnOpenExplorer.Name = "btnOpenExplorer";
            btnOpenExplorer.Size = new Size(147, 23);
            btnOpenExplorer.TabIndex = 4;
            btnOpenExplorer.Text = "保存フォルダを開く";
            btnOpenExplorer.UseVisualStyleBackColor = true;
            // 
            // chkNotify
            // 
            chkNotify.AutoSize = true;
            chkNotify.Location = new Point(12, 120);
            chkNotify.Name = "chkNotify";
            chkNotify.Size = new Size(147, 19);
            chkNotify.TabIndex = 5;
            chkNotify.Text = "保存時に通知を表示する";
            chkNotify.UseVisualStyleBackColor = true;
            // 
            // chkStartup
            // 
            chkStartup.AutoSize = true;
            chkStartup.Location = new Point(12, 145);
            chkStartup.Name = "chkStartup";
            chkStartup.Size = new Size(187, 19);
            chkStartup.TabIndex = 6;
            chkStartup.Text = "Windows起動時に自動実行する";
            chkStartup.UseVisualStyleBackColor = true;
            // 
            // btnSaveConfig
            // 
            btnSaveConfig.Location = new Point(12, 170);
            btnSaveConfig.Name = "btnSaveConfig";
            btnSaveConfig.Size = new Size(300, 23);
            btnSaveConfig.TabIndex = 7;
            btnSaveConfig.Text = "設定を保存して閉じる";
            btnSaveConfig.UseVisualStyleBackColor = true;
            // 
            // notifyIcon1
            // 
            notifyIcon1.Text = "システムトレイ用のアイコン";
            notifyIcon1.Visible = true;
            // 
            // contextMenuStrip1
            // 
            contextMenuStrip1.Items.AddRange(new ToolStripItem[] { menuOpenSetting, menuOpenFolder, toolStripSeparator1, menuExit });
            contextMenuStrip1.Name = "contextMenuStrip1";
            contextMenuStrip1.Size = new Size(162, 76);
            contextMenuStrip1.Text = "システムトレイアイコンの右クリックメニュー";
            // 
            // menuOpenSetting
            // 
            menuOpenSetting.Name = "menuOpenSetting";
            menuOpenSetting.Size = new Size(161, 22);
            menuOpenSetting.Text = "設定画面を開く";
            // 
            // menuOpenFolder
            // 
            menuOpenFolder.Name = "menuOpenFolder";
            menuOpenFolder.Size = new Size(161, 22);
            menuOpenFolder.Text = "保存フォルダを開く";
            // 
            // toolStripSeparator1
            // 
            toolStripSeparator1.Name = "toolStripSeparator1";
            toolStripSeparator1.Size = new Size(158, 6);
            // 
            // menuExit
            // 
            menuExit.Name = "menuExit";
            menuExit.Size = new Size(161, 22);
            menuExit.Text = "終了";
            // 
            // BtnHelp
            // 
            BtnHelp.Location = new Point(12, 12);
            BtnHelp.Name = "BtnHelp";
            BtnHelp.Size = new Size(300, 23);
            BtnHelp.TabIndex = 0;
            BtnHelp.Text = "使い方";
            BtnHelp.UseVisualStyleBackColor = true;
            // 
            // MainForm
            // 
            AutoScaleDimensions = new SizeF(7F, 15F);
            AutoScaleMode = AutoScaleMode.Font;
            ClientSize = new Size(333, 215);
            Controls.Add(BtnHelp);
            Controls.Add(btnSaveConfig);
            Controls.Add(chkStartup);
            Controls.Add(chkNotify);
            Controls.Add(btnOpenExplorer);
            Controls.Add(btnBrowse);
            Controls.Add(txtSavePath);
            Controls.Add(lblSavePath);
            FormBorderStyle = FormBorderStyle.FixedSingle;
            MaximizeBox = false;
            Name = "MainForm";
            StartPosition = FormStartPosition.CenterScreen;
            Text = "ClipOutputImg - 設定";
            Load += Form1_Load;
            contextMenuStrip1.ResumeLayout(false);
            ResumeLayout(false);
            PerformLayout();
        }

        #endregion

        private Label lblSavePath;
        private TextBox txtSavePath;
        private Button btnBrowse;
        private Button btnOpenExplorer;
        private CheckBox chkNotify;
        private CheckBox chkStartup;
        private Button btnSaveConfig;
        private NotifyIcon notifyIcon1;
        private ContextMenuStrip contextMenuStrip1;
        private ToolStripMenuItem menuOpenSetting;
        private ToolStripMenuItem menuOpenFolder;
        private ToolStripSeparator toolStripSeparator1;
        private ToolStripMenuItem menuExit;
        private Button BtnHelp;
    }
}
