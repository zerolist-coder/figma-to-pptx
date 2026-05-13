package com.ppttofigma.backend.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * PPT 표 → 플러그인에서 {@code figma.createTable}(FigJam) 또는 프레임+셀 폴백.
 */
public class TableDataDTO {
    private int numRows;
    private int numColumns;
    /** true 이면 셀 병합이 있어 네이티브 표 API 사용 불가 → 폴백만 */
    private boolean mergedCells;
    private List<Double> columnWidthsPx = new ArrayList<>();
    private List<Double> rowHeightsPx = new ArrayList<>();
    private List<TableCellPieceDTO> pieces = new ArrayList<>();

    public int getNumRows() { return numRows; }
    public void setNumRows(int numRows) { this.numRows = numRows; }
    public int getNumColumns() { return numColumns; }
    public void setNumColumns(int numColumns) { this.numColumns = numColumns; }
    public boolean isMergedCells() { return mergedCells; }
    public void setMergedCells(boolean mergedCells) { this.mergedCells = mergedCells; }
    public List<Double> getColumnWidthsPx() { return columnWidthsPx; }
    public void setColumnWidthsPx(List<Double> columnWidthsPx) { this.columnWidthsPx = columnWidthsPx; }
    public List<Double> getRowHeightsPx() { return rowHeightsPx; }
    public void setRowHeightsPx(List<Double> rowHeightsPx) { this.rowHeightsPx = rowHeightsPx; }
    public List<TableCellPieceDTO> getPieces() { return pieces; }
    public void setPieces(List<TableCellPieceDTO> pieces) { this.pieces = pieces; }
}
