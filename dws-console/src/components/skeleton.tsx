import type { CSSProperties } from "react";

/** Shimmer placeholder. shadcn `skeleton` equivalent, Organic-themed. */
export function Skeleton({
	width,
	height,
	style,
}: {
	width?: number | string;
	height?: number | string;
	style?: CSSProperties;
}) {
	return <span className="skel" style={{ width, height, ...style }} />;
}

/** N skeleton rows sized to a table with the given column widths (%). */
export function SkeletonRows({ rows, cols }: { rows: number; cols: number[] }) {
	return (
		<table className="tbl">
			<tbody>
				{Array.from({ length: rows }, (_, r) => (
					// biome-ignore lint/suspicious/noArrayIndexKey: fixed-count decorative placeholders, no stable id
					<tr key={`skel-row-${r}`}>
						{cols.map((w, c) => (
							// biome-ignore lint/suspicious/noArrayIndexKey: column widths are positional
							<td key={`skel-col-${c}`} style={{ width: `${w}%` }}>
								<Skeleton width={`${Math.max(30, 90 - c * 8)}%`} />
							</td>
						))}
					</tr>
				))}
			</tbody>
		</table>
	);
}
