import requests
from bs4 import BeautifulSoup
import webbrowser
import sys
from tkinter import messagebox
import os
from datetime import datetime, timedelta
import urllib.request, urllib.error

#-----------
#Sub
#-----------
#起動引数出力(True:正常、False異常)
def PrintArgs(vArgs):
	#起動引数の数（引数の増減があればここを修正する）
	intHikisuNum = 4
	#起動引数の意味（引数の増減があればここを修正する）
	ArgStr = ['ファイル名', '基本URL', '任意のURLタイトル（作業ファイル名）', '0:ブラウザ起動有 1:ブラウザ起動無']
	if len(ArgStr) != intHikisuNum or len(vArgs) != intHikisuNum:
		print('起動引数エラー')
		intCnt = 0
		for ArgStr in ArgStr:
			print(str(intCnt) + "=" + ArgStr)
			intCnt = intCnt +1
		intCnt = 0
		for Arg in vArgs:
			print(str(intCnt) + "=" + Arg)
			intCnt = intCnt +1
		return False
	else:
		intCnt = 0
		for Args in vArgs:
			print(str(intCnt) + ':' + ArgStr[intCnt] + "=" + Args)
			intCnt = intCnt +1
		return True

#ファイル出力
def WriteFile(vFile, vLists, vMode):
	f = open(vFile, vMode, encoding='UTF-8')
	f.writelines(vLists)
	f.close()
	return

#ファイルが無い場合は空のファイルを作成する
def CreateEmptyFile(vFile):
	if(os.path.exists(vFile) == False):
		#ファイル出力（新規）
		WriteFile(vFile, '', 'w')
	return

#ファイル取得
def ReadFile(vFile):
	lsLists = []
	#ファイルが無い場合は空のファイルを作成する
	CreateEmptyFile(vFile)
	f = open(vFile, 'r', encoding='UTF-8')
	#改行で分割しない場合、1文字が1つの配列になるため
	#改行で分割して1行を1つの配列に格納する。
	lsLists = f.read().split('\n')
	f.close()
	#改行のみの行を削除
	lsLists = DelNewline(lsLists)
	return lsLists

#改行のみの行を削除
def DelNewline(vLists):
	lsLists = []
	for sData in vLists:
		if len(sData) > 0:
			lsLists.append(sData)
	return lsLists

#重複除去、ソート
def RepeatedAndSort(vLists):
	vLists = set(vLists)
	vLists = sorted(vLists)
	return vLists

#差分
def Difference(vBases, vChecks):
	lsLists = []
	for sBase in vBases:
		intFlag = 0
		for sCheck in vChecks:
			#改行を除去して比較
			if sBase.replace('\n', '') == sCheck.replace('\n', ''):
				intFlag = 1
				break
		if intFlag == 0:
			lsLists.append(sBase)
	return lsLists

#ブラウザ起動
def BrowserOpen(vLists):
	for sUrl in vLists:
		webbrowser.open(sUrl)
	return

#除外対象でないURLを追加する(True:除外する、False:除外しない)
def CheckExclude(vUrl, vLists):
	intFind = 0
	for sExclude in vLists:
		if len(sExclude) > 0:
			#小文字して比較
			if vUrl.lower().find(sExclude.lower()) >= 0:
				intFind = 1
				break
	if intFind == 0:
		return False
	else:
		return  True

#10週間以上前のチェック(0:10週間超、1:10週間以内)
def CheckPastDay(vstrData):
	sYYYY = ''
	sMM = ''
	sDD = ''
	tdLogDay = datetime.now()
	#10週間前
	tdPastDay = datetime.now() - timedelta(weeks=10)
	if len(vstrData) >= 10:
		sYYYY = vstrData[0:4]
		sMM = vstrData[5:7]
		sDD = vstrData[8:10]
		if sYYYY.isdigit() == True or sMM.isdigit() == True or sDD.isdigit() == True:
			tdLogDay = datetime(int(sYYYY), int(sMM), int(sDD))
			#10週間以内の場合
			if tdLogDay >= tdPastDay:
				#10週間以内
				return 1
	#10週間超
	return 0

#10週間以上前のログ削除
def LogDel(vFile):
	lsReadLists = []
	lsWriteLists = []
	intFind = 0
	lsReadLists = ReadFile(vFile)
	for sData in lsReadLists:
		if len(sData) > 0:
			if intFind == 0:
				#10週間以上前のチェック(0:10週間超、1:10週間以内)
				intFind = CheckPastDay(sData)
			if intFind == 1:
				#10週間以内
				lsWriteLists.append(sData)
				lsWriteLists.append('\n')
	#ファイル出力（新規）
	WriteFile(vFile, lsWriteLists, 'w')
	del lsReadLists
	del lsWriteLists
	return

def CheckURLExt(url):
	try:
		f = urllib.request.urlopen(url)
		f.close()
		return 1
	except:
		return 0

#-----------
#Main
#-----------
#起動引数
Args = sys.argv
#起動引数出力(True:正常、False異常)
if PrintArgs(Args) == False:
	sys.exit( )

BaseUrl = Args[1]
FileName = Args[2] 
BrowserStart = Args[3]

#取得したい記事があるURL（今のところ全て基本URLの下にこの記載があるため）
CheckUrl_1 = BaseUrl + 'archives/'
CheckUrl_2 = BaseUrl + 'articles/'

#ファイル名
ThisFile = FileName + '_ThisURL.txt'
LogFile = FileName + '_LogURL.txt'
OpenFile = FileName + '_OpenURL.txt'
ExcludeFile = FileName + '_ExcludeString.txt'
OrgFile = FileName + '_OrgFile.txt'

#messagebox.showinfo('確認', BaseUrl + '\n' + CheckUrl + '\n' + ThisFile + '\n' + LogFile + '\n' + OpenFile + '\n' + ExcludeFile + '\n' + OrgFile + '\n' + BrowserStart)

lsNowLists = []
lsNowLists.append(str(datetime.now()) + '\n')
#----------------------
#除外文字取得（不要なURLを開かないようにする）
#----------------------
#ファイル取得
listExclude = []
listExclude = ReadFile(ExcludeFile)
#重複除去、ソート
listExclude = RepeatedAndSort(listExclude)

#----------------------
#今回のURL取得
#----------------------
listThisURL = []

#HTML取得
# 一部サイトでは User-Agent が無いと HTML を返さないため、ブラウザ相当のヘッダを付与する
headers = {
	'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36'
}
html = requests.get(BaseUrl, headers=headers)
soup = BeautifulSoup(html.content, "html.parser")
#全てのaタグ要素を取得
links = soup.find_all('a')

#空のhref出力
WriteFile(OrgFile, '', 'w')

for link in links:
	#URL取得(タグ付き)
	linkstr = str(link)
	#URL出力(タグ付き)
	WriteFile(OrgFile, linkstr + '\n', 'a')
	#URL取得（URLのみ）
	linkhref = str(link.get('href'))
	if len(linkhref) > 0:
		#小文字して比較
		if (linkstr.lower().find(CheckUrl_1.lower()) >= 0 or linkstr.lower().find(CheckUrl_2.lower()) >= 0) and linkstr.lower().find('.html') >= 0:
			#除外対象でないURLを追加する(True:除外する、False:除外しない)
			if CheckExclude(linkhref, listExclude) == False:
				listThisURL.append(linkhref + '\n')

#重複除去、ソート
listThisURL = RepeatedAndSort(listThisURL)

#ファイル出力（新規）
WriteFile(ThisFile, lsNowLists, 'w')
WriteFile(ThisFile, listThisURL, 'a')

#----------------------
#過去に起動済のURL取得
#----------------------
listLogURL = []

#ファイル取得
listLogURL = ReadFile(LogFile)
#重複除去、ソート
listLogURL = RepeatedAndSort(listLogURL)

#----------------------
#今回のURLと過去のURLを比較し、一致しないURLをブラウザで起動
#----------------------
listOpenURL = []

#差分
listOpenURL = Difference(listThisURL, listLogURL)

#重複除去、ソート
listOpenURL = RepeatedAndSort(listOpenURL)

#ファイル出力（新規）
WriteFile(OpenFile, lsNowLists, 'w')
WriteFile(OpenFile, listOpenURL, 'a')

#引数によるブラウザ起動制御
if BrowserStart != '1':
	#ブラウザ起動
	BrowserOpen(listOpenURL)

#----------------------
#起動したURLをログ出力
#----------------------
#ファイル出力（追加）
WriteFile(LogFile, lsNowLists, 'a')
WriteFile(LogFile, listOpenURL, 'a')

#----------------------
#10週間以上前のログ削除
#----------------------
#ログファイル調整（新規）
LogDel(LogFile)

#----------------------
#終了処理
#----------------------
#メモリ開放
del listExclude
del listThisURL
del listLogURL
del listOpenURL
